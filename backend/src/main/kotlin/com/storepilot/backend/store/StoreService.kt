package com.storepilot.backend.store

import com.storepilot.backend.admin.AdminNotificationService
import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.storage.FileUploadPolicies
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.seller.SellerRepository
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException
import java.math.BigDecimal
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — see docs/gaps-and-assumptions.md's search-scalability note. */
private const val MAX_PAGE_SIZE = 100

@Service
@Transactional(readOnly = true)
class StoreService(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val sellerRepository: SellerRepository,
    private val currentActor: CurrentActor,
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
    private val fileStorageService: FileStorageService,
    private val adminNotificationService: AdminNotificationService,
    private val platformConfigService: PlatformConfigService,
    private val auditLogService: AuditLogService,
    private val orderRepository: OrderRepository,
    private val followRepository: FollowRepository,
    private val bookingRepository: BookingRepository,
    private val payoutRepository: PayoutRepository,
    private val feeCollectionRepository: FeeCollectionRepository,
) {
    private val log = LoggerFactory.getLogger(StoreService::class.java)

    /**
     * GET /api/stores — public marketplace listing: active stores only.
     * Filtering and sorting both happen in the SQL query (Specification +
     * Pageable) — the DB is only ever asked for one page's worth of rows.
     */
    fun search(category: String?, query: String?, page: Int, size: Int): PageResponse<StoreResponse> {
        val categoryEnum = category?.let { wireValueOf<StoreCategory>(it) }

        val activeOnly = Specification<Store> { root, _, cb ->
            cb.equal(root.get<StoreVerificationStatus>("verificationStatus"), StoreVerificationStatus.ACTIVE)
        }
        val categorySpec: Specification<Store>? = categoryEnum?.let { cat ->
            Specification { root, _, cb -> cb.equal(root.get<StoreCategory>("category"), cat) }
        }
        val querySpec: Specification<Store>? = query?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val pattern = "%${q.lowercase()}%"
            Specification { root, _, cb ->
                cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("tagline")), pattern),
                    cb.like(cb.lower(root.get<StoreAddress>("address").get<String>("city")), pattern),
                )
            }
        }

        var spec = activeOnly
        if (categorySpec != null) spec = spec.and(categorySpec)
        if (querySpec != null) spec = spec.and(querySpec)

        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE), Sort.by("rating").descending())
        val results = storeRepository.findAll(spec, pageable)
        return results.toPageResponse { it.toResponse(fileStorageService) }
    }

    /** GET /api/stores/{slug} — public storefront: active stores only. */
    fun getBySlug(slug: String): StoreResponse {
        val store = storeRepository.findBySlug(slug)?.takeIf { it.verificationStatus == StoreVerificationStatus.ACTIVE }
            ?: throw NotFoundException("Store $slug not found")
        return store.toResponse(fileStorageService)
    }

    /** GET /api/stores/id/{id} — internal lookup, not gated by verification status. */
    fun getById(id: UUID): StoreResponse =
        storeRepository.findById(id).orElseThrow { NotFoundException("Store $id not found") }.toResponse(fileStorageService)

    /**
     * GET /api/stores/{storeId}/settings — full verification/bank/contact
     * details, restricted to the owning seller (SecurityConfig gates the
     * method to hasRole("SELLER"); requireOwnedStore below enforces it's
     * *their* store). Buyer-facing checkout/order pages use
     * getPublicSettings instead, which excludes PII.
     */
    fun getSettings(storeId: UUID): StoreSettingsResponse? {
        requireOwnedStore(storeId)
        return storeSettingsRepository.findById(storeId).orElse(null)?.toResponse(fileStorageService)
    }

    /**
     * GET /api/stores/{storeId}/stats — dashboard trend cards. Rolling
     * windows rather than calendar weeks (simpler, no timezone-boundary
     * edge cases) — the last 7 days vs the 7 days before that, both ending
     * "now". Revenue/fees only count PAID, non-cancelled orders and
     * bookings — the same filter the dashboard's own client-side revenue
     * reduce already uses. Bookings are summed in alongside orders (not
     * just orders alone) so a bookings-only seller with no products still
     * sees their real revenue here, not $0.
     */
    fun getStats(storeId: UUID): StoreStatsResponse {
        requireOwnedStore(storeId)
        val now = java.time.Instant.now()
        val currentFrom = now.minus(7, java.time.temporal.ChronoUnit.DAYS)
        val previousFrom = now.minus(14, java.time.temporal.ChronoUnit.DAYS)
        return StoreStatsResponse(
            revenueCurrentPeriod = orderRepository.sumSubtotalForPaidOrders(storeId, currentFrom, now) +
                bookingRepository.sumServicePriceForPaidBookings(storeId, currentFrom, now),
            revenuePreviousPeriod = orderRepository.sumSubtotalForPaidOrders(storeId, previousFrom, currentFrom) +
                bookingRepository.sumServicePriceForPaidBookings(storeId, previousFrom, currentFrom),
            platformFeeCurrentPeriod = orderRepository.sumPlatformFeeForPaidOrders(storeId, currentFrom, now) +
                bookingRepository.sumPlatformFeeForPaidBookings(storeId, currentFrom, now),
            platformFeePreviousPeriod = orderRepository.sumPlatformFeeForPaidOrders(storeId, previousFrom, currentFrom) +
                bookingRepository.sumPlatformFeeForPaidBookings(storeId, previousFrom, currentFrom),
        )
    }

    /**
     * GET /api/stores/{storeId}/follow — works for a signed-out visitor too
     * (permitAll route), just reports false — see
     * CookieBearerTokenResolver's doc comment on optional-auth guest
     * routes. Plain @Transactional (not the class default readOnly), same
     * reasoning as BuyerService.getCurrent() — buyerOrNull() may
     * JIT-provision a row on this caller's first request.
     */
    @Transactional
    fun isFollowing(storeId: UUID): Boolean {
        val buyer = currentActor.buyerOrNull() ?: return false
        return followRepository.existsByBuyerIdAndStoreId(requireNotNull(buyer.id), storeId)
    }

    /** POST /api/stores/{storeId}/follow — idempotent: following an already-followed store is a silent no-op, not a 409. */
    @Transactional
    fun follow(storeId: UUID): Boolean {
        val buyer = currentActor.requireBuyer()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val buyerId = requireNotNull(buyer.id)
        if (!followRepository.existsByBuyerIdAndStoreId(buyerId, storeId)) {
            followRepository.save(Follow(buyer = buyer, store = store))
            store.followerCount += 1
            storeRepository.save(store)
        }
        return true
    }

    /** DELETE /api/stores/{storeId}/follow — idempotent: unfollowing a store you don't follow is a silent no-op. */
    @Transactional
    fun unfollow(storeId: UUID) {
        val buyer = currentActor.requireBuyer()
        val buyerId = requireNotNull(buyer.id)
        val follow = followRepository.findByBuyerIdAndStoreId(buyerId, storeId) ?: return
        followRepository.delete(follow)
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        store.followerCount = (store.followerCount - 1).coerceAtLeast(0)
        storeRepository.save(store)
    }

    /** GET /api/stores/{storeId}/public-settings — buyer-safe subset, no auth required. */
    fun getPublicSettings(storeId: UUID): StorePublicSettingsResponse? =
        storeSettingsRepository.findById(storeId).orElse(null)?.toPublicResponse()

    /**
     * GET /api/me/store — the authenticated seller's own store, or null if
     * they haven't onboarded yet. Lets the frontend resolve "my storeId"
     * itself instead of trusting a client-supplied value.
     */
    fun getMyStore(): StoreResponse? {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        return storeRepository.findBySellerId(sellerId)?.toResponse(fileStorageService)
    }

    /**
     * POST /api/stores — onboarding. Creates a new store in "pending"
     * verification status, and — the first time a given Cognito identity
     * onboards — a Seller row + the Cognito "seller" group membership, in
     * the same transaction. Not gated by hasRole("SELLER") at the filter
     * chain (SecurityConfig only requires .authenticated() here) since this
     * exact call is what grants that role for a first-time seller.
     */
    @Transactional
    fun create(input: StoreApplicationInput): StoreResponse {
        val identity = currentActor.currentIdentityOrNull() ?: throw ForbiddenException("Authentication required")
        // Buyer and seller are mutually exclusive identities (see
        // AuthController.register()'s doc comment) — refuse to let an
        // existing buyer account also become a seller, rather than
        // silently polluting it with both Cognito groups.
        if (currentActor.isBuyer()) {
            throw ConflictException("This account is registered as a buyer — create a separate account to sell.")
        }
        val seller = sellerRepository.findByCognitoSub(identity.sub) ?: run {
            val created = sellerRepository.save(Seller(cognitoSub = identity.sub, email = identity.email, name = identity.name))
            grantSellerGroup(identity.username)
            created
        }

        // Idempotent retry, not a second store: the onboarding page's
        // mutationFn calls this endpoint, then PATCHes /settings, then
        // uploads documents, as separate HTTP requests — if any later step
        // fails (e.g. a bad ABN checksum) the Store row from this step has
        // already committed, and the user's natural next move is to fix the
        // field and resubmit the whole form. Without this check, that retry
        // called create() again and produced a second Store row for the
        // same seller, which permanently breaks storeRepository
        // .findBySellerId() (used by the dashboard) — it assumes at most
        // one row and throws IncorrectResultSizeDataAccessException
        // otherwise. An already-ACTIVE or CLOSED store is never touched
        // here — reusing this path against a live store would silently
        // overwrite its public listing from a stray resubmission.
        val existing = storeRepository.findBySellerId(requireNotNull(seller.id))
        if (existing != null) {
            if (existing.verificationStatus != StoreVerificationStatus.PENDING &&
                existing.verificationStatus != StoreVerificationStatus.REJECTED
            ) {
                throw ConflictException("You already have a store — manage it from your dashboard instead.")
            }
            if (existing.name != input.name) existing.slug = uniqueSlug(input.name)
            existing.name = input.name
            existing.tagline = input.tagline
            existing.description = input.description
            existing.category = wireValueOf(input.category)
            existing.address = StoreAddress(city = input.city, state = input.state)
            existing.whatsappNumber = input.whatsappNumber
            existing.verificationStatus = StoreVerificationStatus.PENDING
            return storeRepository.save(existing).toResponse(fileStorageService)
        }

        val slug = uniqueSlug(input.name)
        val store = Store(
            seller = seller,
            slug = slug,
            name = input.name,
            tagline = input.tagline,
            description = input.description,
            // logoUrl/bannerUrl start null — the frontend renders a
            // generated initials avatar / color block until the seller
            // uploads real images via uploadLogo/uploadBanner below.
            category = wireValueOf(input.category),
            address = StoreAddress(
                city = input.city,
                state = input.state,
            ),
            whatsappNumber = input.whatsappNumber,
            verificationStatus = StoreVerificationStatus.PENDING,
        )
        return storeRepository.save(store).toResponse(fileStorageService)
    }

    /** Retries once — if this ultimately fails, the exception aborts create()'s whole transaction (rolling back the Store + Seller insert together) rather than leaving a seller with a store but no role. */
    private fun grantSellerGroup(username: String) {
        repeat(2) { attempt ->
            try {
                cognitoClient.adminAddUserToGroup(
                    AdminAddUserToGroupRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId)
                        .username(username)
                        .groupName("seller")
                        .build(),
                )
                return
            } catch (e: CognitoIdentityProviderException) {
                if (attempt == 1) {
                    log.error("Failed to add {} to Cognito 'seller' group after retry — aborting onboarding", username, e)
                    throw e
                }
            }
        }
    }

    /** PATCH /api/stores/{storeId}/settings — verifies the caller owns this store before delegating to the shared upsert logic below (also used internally by the admin rejection path in setVerificationStatus, which is already gated by SecurityConfig's hasRole("ADMIN") on the admin path prefix, not this check). */
    @Transactional
    fun updateSettingsAsSeller(storeId: UUID, input: StoreSettingsInput): StoreSettingsResponse {
        val store = requireOwnedStore(storeId)
        return upsertSettings(storeId, input)
    }

    /** PATCH /api/stores/{storeId}/profile — public-facing social links, separate from settings since those are private payout/verification data. */
    @Transactional
    fun updateProfileAsSeller(storeId: UUID, input: StoreProfileInput): StoreResponse {
        val store = requireOwnedStore(storeId)
        if (input.facebookUrl != null) store.facebookUrl = input.facebookUrl.trim().takeIf { it.isNotBlank() }
        if (input.instagramUrl != null) store.instagramUrl = input.instagramUrl.trim().takeIf { it.isNotBlank() }
        if (input.tiktokUrl != null) store.tiktokUrl = input.tiktokUrl.trim().takeIf { it.isNotBlank() }
        return storeRepository.save(store).toResponse(fileStorageService)
    }

    private fun requireOwnedStore(storeId: UUID): Store {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        return store
    }

    private fun upsertSettings(storeId: UUID, input: StoreSettingsInput): StoreSettingsResponse {
        val existing = storeSettingsRepository.findById(storeId).orElse(null)
        if (existing != null) {
            // Once a store is ACTIVE, its identity-verification fields are
            // frozen against direct edits — a seller changing what
            // legal/business identity they claim to be, after an admin has
            // already approved it against that claim, needs re-review (see
            // task item 40's decision). uploadDriverLicenceDocument etc.
            // enforce the same freeze for the documents backing these
            // fields. This never blocks PENDING/REJECTED stores' initial
            // submission/resubmission, only post-approval edits — those go
            // through StoreVerificationChangeRequestService instead.
            if (existing.store.verificationStatus == StoreVerificationStatus.ACTIVE && hasVerificationFieldEdits(input)) {
                throw ConflictException(
                    "This store is already approved — submit a verification change request instead of editing these fields directly",
                )
            }

            // Snapshot before mutating — this is the only way to tell a real
            // change apart from the caller simply resubmitting the same
            // values (the frontend settings form always resubmits the full
            // shape, not a diff).
            val bankDetailsChanged =
                (input.bankName != null && input.bankName != existing.bankName) ||
                    (input.bankAccountName != null && input.bankAccountName != existing.bankAccountName) ||
                    (input.bankAccountNumber != null && input.bankAccountNumber != existing.bankAccountNumber)

            val sellerPlan = existing.store.seller.plan
            input.contactEmail?.let { existing.contactEmail = it }
            input.contactPhone?.let { existing.contactPhone = it }
            input.bankAccountName?.let { existing.bankAccountName = it }
            input.bankAccountNumber?.let { existing.bankAccountNumber = it }
            input.bankName?.let { existing.bankName = it }
            input.transactionFeePercent?.let { existing.transactionFeePercent = it }
            // Cash on Delivery and Bank transfer are Pro-only (see
            // SellerPlan.kt) — force off regardless of what was requested
            // whenever the seller isn't Pro, rather than rejecting the
            // whole settings save, so editing an unrelated field never
            // fails because of a stale/bypassed client-side toggle.
            input.codEnabled?.let { existing.codEnabled = it && sellerPlan == SellerPlan.PRO }
            input.onlinePaymentEnabled?.let { existing.onlinePaymentEnabled = it }
            input.bankTransferEnabled?.let { existing.bankTransferEnabled = it && sellerPlan == SellerPlan.PRO }
            // Only reachable below when the store isn't ACTIVE yet (the
            // guard above already threw otherwise) — i.e. this is still the
            // initial onboarding submission or a post-rejection resubmission.
            input.sellerType?.let { existing.sellerType = wireValueOf(it) }
            input.driverLicenceNumber?.let { existing.driverLicenceNumber = it }
            if (input.abn != null) existing.abn = input.abn
            input.nicNumber?.let { existing.nicNumber = it }
            if (input.businessRegistrationNumber != null) existing.businessRegistrationNumber = input.businessRegistrationNumber
            if (input.rejectionReason != null) existing.rejectionReason = input.rejectionReason
            input.stockManagementEnabled?.let { existing.stockManagementEnabled = it }
            input.pickupEnabled?.let { existing.pickupEnabled = it }
            input.stripeEnabled?.let { existing.stripeEnabled = it }
            input.bookingsEnabled?.let { existing.bookingsEnabled = it }
            input.gstRegistered?.let { existing.gstRegistered = it }
            requireAtLeastOnePaymentMethod(existing.codEnabled, existing.onlinePaymentEnabled, existing.bankTransferEnabled)
            requireCountryVerificationFields(existing)
            val saved = storeSettingsRepository.save(existing)
            if (bankDetailsChanged) {
                adminNotificationService.notifyBankDetailsChanged(
                    saved.store,
                    saved.bankName,
                    saved.bankAccountName,
                    saved.bankAccountNumber,
                )
            }
            // input.rejectionReason != null is the one caller of this method
            // that isn't a genuine seller-initiated edit — setVerificationStatus
            // (admin rejecting) reuses upsertSettings to stash the rejection
            // reason, and that path already gets its own STORE_REJECTED audit
            // row, so recording a second one here would be a duplicate.
            if (input.rejectionReason == null) {
                currentActor.sellerOrNull()?.let { seller ->
                    auditLogService.recordAsSeller(
                        seller,
                        AuditAction.STORE_SETTINGS_UPDATED,
                        "store",
                        storeId.toString(),
                        "Updated settings for \"${saved.store.name}\"",
                    )
                }
            }
            return saved.toResponse(fileStorageService)
        }

        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val platformConfig = platformConfigService.current()
        val sellerIsPro = store.seller.plan == SellerPlan.PRO
        // See the equivalent gate above for why COD/bank-transfer are
        // forced off rather than rejecting the request outright.
        val codEnabled = (input.codEnabled ?: platformConfig.defaultCodEnabled) && sellerIsPro
        val onlinePaymentEnabled = input.onlinePaymentEnabled ?: platformConfig.defaultOnlinePaymentEnabled
        val bankTransferEnabled = (input.bankTransferEnabled ?: platformConfig.defaultBankTransferEnabled) && sellerIsPro
        requireAtLeastOnePaymentMethod(codEnabled, onlinePaymentEnabled, bankTransferEnabled)
        val created = StoreSettings(
            store = store,
            contactEmail = input.contactEmail ?: "",
            contactPhone = input.contactPhone ?: "",
            bankAccountName = input.bankAccountName ?: "",
            bankAccountNumber = input.bankAccountNumber ?: "",
            bankName = input.bankName ?: "",
            transactionFeePercent = input.transactionFeePercent ?: platformConfig.platformFeePercent,
            codEnabled = codEnabled,
            onlinePaymentEnabled = onlinePaymentEnabled,
            bankTransferEnabled = bankTransferEnabled,
            sellerType = input.sellerType?.let { wireValueOf<SellerType>(it) } ?: SellerType.INDIVIDUAL,
            driverLicenceNumber = input.driverLicenceNumber,
            abn = input.abn,
            nicNumber = input.nicNumber,
            businessRegistrationNumber = input.businessRegistrationNumber,
            rejectionReason = input.rejectionReason,
            stockManagementEnabled = input.stockManagementEnabled ?: true,
            pickupEnabled = input.pickupEnabled ?: false,
            gstRegistered = input.gstRegistered ?: false,
            bookingsEnabled = input.bookingsEnabled ?: false,
            stripeEnabled = input.stripeEnabled ?: false,
        )
        requireCountryVerificationFields(created)
        return storeSettingsRepository.save(created).toResponse(fileStorageService)
    }

    private fun hasVerificationFieldEdits(input: StoreSettingsInput): Boolean =
        input.sellerType != null || input.driverLicenceNumber != null || input.abn != null ||
            input.nicNumber != null || input.businessRegistrationNumber != null

    /** Shared by upsertSettings and the 4 document-upload methods below — see upsertSettings' doc comment for why. */
    private fun requireNotActiveForDirectVerificationEdit(store: Store) {
        if (store.verificationStatus == StoreVerificationStatus.ACTIVE) {
            throw ConflictException(
                "This store is already approved — submit a verification change request instead of replacing this document directly",
            )
        }
    }

    /** See the shared requireCountryVerificationFields(...) top-level function's doc comment. */
    private fun requireCountryVerificationFields(settings: StoreSettings) {
        requireCountryVerificationFields(
            platformConfigService.current().countryCode,
            settings.sellerType,
            settings.driverLicenceNumber,
            settings.abn,
            settings.nicNumber,
            settings.businessRegistrationNumber,
            settings.gstRegistered,
        )
    }

    /** POST /api/stores/{storeId}/driver-licence-document — seller uploads/replaces their driver's licence proof. */
    @Transactional
    fun uploadDriverLicenceDocument(storeId: UUID, file: MultipartFile): StoreSettingsResponse {
        requireNotActiveForDirectVerificationEdit(requireOwnedStore(storeId))
        val reference = fileStorageService.store(
            "seller-documents",
            file,
            FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
            FileUploadPolicies.DOCUMENT_MAX_BYTES,
        )
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        settings.driverLicenceDocumentUrl = reference
        return storeSettingsRepository.save(settings).toResponse(fileStorageService)
    }

    /** POST /api/stores/{storeId}/abn-document — seller uploads/replaces their ABN registration proof. */
    @Transactional
    fun uploadAbnDocument(storeId: UUID, file: MultipartFile): StoreSettingsResponse {
        requireNotActiveForDirectVerificationEdit(requireOwnedStore(storeId))
        val reference = fileStorageService.store(
            "seller-documents",
            file,
            FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
            FileUploadPolicies.DOCUMENT_MAX_BYTES,
        )
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        settings.abnDocumentUrl = reference
        return storeSettingsRepository.save(settings).toResponse(fileStorageService)
    }

    /** POST /api/stores/{storeId}/nic-document — seller uploads/replaces their NIC proof (Sri Lanka deployments only). */
    @Transactional
    fun uploadNicDocument(storeId: UUID, file: MultipartFile): StoreSettingsResponse {
        requireNotActiveForDirectVerificationEdit(requireOwnedStore(storeId))
        val reference = fileStorageService.store(
            "seller-documents",
            file,
            FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
            FileUploadPolicies.DOCUMENT_MAX_BYTES,
        )
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        settings.nicDocumentUrl = reference
        return storeSettingsRepository.save(settings).toResponse(fileStorageService)
    }

    /** POST /api/stores/{storeId}/business-reg-document — seller uploads/replaces their business registration proof (Sri Lanka deployments only). */
    @Transactional
    fun uploadBusinessRegDocument(storeId: UUID, file: MultipartFile): StoreSettingsResponse {
        requireNotActiveForDirectVerificationEdit(requireOwnedStore(storeId))
        val reference = fileStorageService.store(
            "seller-documents",
            file,
            FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
            FileUploadPolicies.DOCUMENT_MAX_BYTES,
        )
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        settings.businessRegDocumentUrl = reference
        return storeSettingsRepository.save(settings).toResponse(fileStorageService)
    }

    /** POST /api/stores/{storeId}/logo — seller uploads/replaces their store logo. */
    @Transactional
    fun uploadLogo(storeId: UUID, file: MultipartFile): StoreResponse {
        val store = requireOwnedStore(storeId)
        store.logoUrl = fileStorageService.store(
            "store-images",
            file,
            FileUploadPolicies.IMAGE_CONTENT_TYPES,
            FileUploadPolicies.IMAGE_MAX_BYTES,
        )
        return storeRepository.save(store).toResponse(fileStorageService)
    }

    /** POST /api/stores/{storeId}/banner — seller uploads/replaces their store banner. */
    @Transactional
    fun uploadBanner(storeId: UUID, file: MultipartFile): StoreResponse {
        val store = requireOwnedStore(storeId)
        store.bannerUrl = fileStorageService.store(
            "store-images",
            file,
            FileUploadPolicies.IMAGE_CONTENT_TYPES,
            FileUploadPolicies.IMAGE_MAX_BYTES,
        )
        return storeRepository.save(store).toResponse(fileStorageService)
    }

    /** A store must always accept at least one payment method — otherwise checkout has nothing to offer buyers. */
    private fun requireAtLeastOnePaymentMethod(codEnabled: Boolean, onlinePaymentEnabled: Boolean, bankTransferEnabled: Boolean) {
        if (!codEnabled && !onlinePaymentEnabled && !bankTransferEnabled) {
            throw ConflictException("At least one payment method (Cash on Delivery, Online payment, or Bank transfer) must stay enabled")
        }
    }

    // --- Admin — gated by SecurityConfig's hasRole("ADMIN") on /api/admin/** ---

    fun adminList(status: String?): List<StoreResponse> {
        val results = if (status != null) {
            storeRepository.findByVerificationStatus(wireValueOf(status))
        } else {
            storeRepository.findAll()
        }
        return results.sortedByDescending { it.createdAt }.map { it.toResponse(fileStorageService) }
    }

    /**
     * GET /api/admin/stores/{storeId}/settings — same shape as the
     * seller-facing/public getSettings above, but not ownership-gated
     * (admin can review any store's verification details/bank info
     * regardless of who owns it). No unauthenticated caller can reach this
     * — it lives under the admin prefix, which SecurityConfig already
     * gates to hasRole("ADMIN") as a whole.
     */
    fun adminGetSettings(storeId: UUID): StoreSettingsResponse? =
        storeSettingsRepository.findById(storeId).orElse(null)?.toResponse(fileStorageService)

    @Transactional
    fun setVerificationStatus(storeId: UUID, input: VerificationDecisionInput): StoreResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val status = wireValueOf<StoreVerificationStatus>(input.status)
        store.verificationStatus = status
        store.isVerified = status == StoreVerificationStatus.ACTIVE
        storeRepository.save(store)

        if (status == StoreVerificationStatus.REJECTED && input.rejectionReason != null) {
            // Stashes the reason directly rather than routing through
            // upsertSettings — that method runs requireAtLeastOnePaymentMethod
            // and the country-verification-fields checks meant for a
            // seller's own edits, and a store can legitimately be pending
            // review with no payment method configured yet (or any other
            // incomplete state) — that's exactly the kind of application an
            // admin needs to be *able* to reject, not blocked from
            // rejecting by validation that has nothing to do with the
            // decision being made.
            storeSettingsRepository.findById(storeId).ifPresent {
                it.rejectionReason = input.rejectionReason
                storeSettingsRepository.save(it)
            }
        }

        // The audit log is the durable history of this decision — unlike
        // StoreSettings.rejectionReason, which gets overwritten on every
        // rejection, each row here is a permanent record of who decided
        // what and why, so a store rejected twice doesn't lose the first
        // reason.
        if (status == StoreVerificationStatus.ACTIVE) {
            auditLogService.record(AuditAction.STORE_APPROVED, "store", storeId.toString(), "Approved store \"${store.name}\"")
        } else if (status == StoreVerificationStatus.REJECTED) {
            val reasonSuffix = input.rejectionReason?.let { ": $it" } ?: ""
            auditLogService.record(AuditAction.STORE_REJECTED, "store", storeId.toString(), "Rejected store \"${store.name}\"$reasonSuffix")
        }
        return store.toResponse(fileStorageService)
    }

    /**
     * POST /api/stores/{storeId}/close — seller-initiated, permanent. Blocked
     * while anything is still in flight or owed either direction, so once it
     * succeeds a seller can safely move on to account deletion (see
     * SellerAccountService) with no orphaned obligations left behind. The
     * store's own identity fields (name/slug/description/logo) are
     * deliberately left untouched — search()/getBySlug() already only
     * surface ACTIVE stores, so closing one drops it out of the public
     * marketplace with no other code changes, while a past buyer's order
     * history keeps showing a coherent store name.
     */
    @Transactional
    fun closeStore(storeId: UUID): StoreResponse {
        val store = requireOwnedStore(storeId)
        if (store.verificationStatus == StoreVerificationStatus.CLOSED) return store.toResponse(fileStorageService)

        if (orderRepository.existsByStoreIdAndStatusIn(storeId, setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.SHIPPED))) {
            throw ConflictException("This store has orders still in progress — resolve them before closing")
        }
        if (bookingRepository.existsByStoreIdAndStatusIn(storeId, setOf(BookingStatus.PENDING, BookingStatus.CONFIRMED))) {
            throw ConflictException("This store has bookings still in progress — resolve them before closing")
        }
        if (feeCollectionRepository.existsByStoreIdAndStatus(storeId, FeeCollectionStatus.PENDING)) {
            throw ConflictException("This store has an outstanding platform fee — settle it before closing")
        }
        if (payoutRepository.existsByStoreIdAndStatus(storeId, PayoutStatus.SCHEDULED)) {
            throw ConflictException("This store has a payout still scheduled — wait for it to complete before closing")
        }

        store.verificationStatus = StoreVerificationStatus.CLOSED
        store.isVerified = false
        val saved = storeRepository.save(store)
        auditLogService.recordAsSeller(
            store.seller,
            AuditAction.STORE_CLOSED,
            "store",
            storeId.toString(),
            "Closed store \"${store.name}\"",
        )
        return saved.toResponse(fileStorageService)
    }

    private fun uniqueSlug(name: String): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "store" }
        var candidate = base
        var suffix = 1
        while (storeRepository.findBySlug(candidate) != null) {
            candidate = "$base-${++suffix}"
        }
        return candidate
    }
}
