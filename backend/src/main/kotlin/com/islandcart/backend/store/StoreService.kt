package com.islandcart.backend.store

import com.islandcart.backend.abn.isValidAbnChecksum
import com.islandcart.backend.admin.AdminNotificationService
import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.PageResponse
import com.islandcart.backend.common.PlatformConfigService
import com.islandcart.backend.common.security.CurrentActor
import com.islandcart.backend.common.security.CognitoProperties
import com.islandcart.backend.common.storage.FileStorageService
import com.islandcart.backend.common.storage.FileUploadPolicies
import com.islandcart.backend.common.toPageResponse
import com.islandcart.backend.common.wireValueOf
import com.islandcart.backend.seller.Seller
import com.islandcart.backend.seller.SellerRepository
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
        return results.toPageResponse { it.toResponse() }
    }

    /** GET /api/stores/{slug} — public storefront: active stores only. */
    fun getBySlug(slug: String): StoreResponse {
        val store = storeRepository.findBySlug(slug)?.takeIf { it.verificationStatus == StoreVerificationStatus.ACTIVE }
            ?: throw NotFoundException("Store $slug not found")
        return store.toResponse()
    }

    /** GET /api/stores/id/{id} — internal lookup, not gated by verification status. */
    fun getById(id: UUID): StoreResponse =
        storeRepository.findById(id).orElseThrow { NotFoundException("Store $id not found") }.toResponse()

    fun getSettings(storeId: UUID): StoreSettingsResponse? =
        storeSettingsRepository.findById(storeId).orElse(null)?.toResponse(fileStorageService)

    /**
     * GET /api/me/store — the authenticated seller's own store, or null if
     * they haven't onboarded yet. Lets the frontend resolve "my storeId"
     * itself instead of trusting a client-supplied value.
     */
    fun getMyStore(): StoreResponse? {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        return storeRepository.findBySellerId(sellerId)?.toResponse()
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
        val seller = sellerRepository.findByCognitoSub(identity.sub) ?: run {
            val created = sellerRepository.save(Seller(cognitoSub = identity.sub, email = identity.email, name = identity.name))
            grantSellerGroup(identity.sub)
            created
        }

        val slug = uniqueSlug(input.name)
        val store = Store(
            seller = seller,
            slug = slug,
            name = input.name,
            tagline = input.tagline,
            description = input.description,
            // Matches the frontend mock's placeholder-image convention —
            // real upload support doesn't exist yet on either side.
            logoUrl = "https://picsum.photos/seed/$slug-logo/200/200",
            bannerUrl = "https://picsum.photos/seed/$slug-banner/1200/400",
            category = wireValueOf(input.category),
            address = StoreAddress(
                city = input.city,
                state = input.state,
            ),
            whatsappNumber = input.whatsappNumber,
            verificationStatus = StoreVerificationStatus.PENDING,
        )
        return storeRepository.save(store).toResponse()
    }

    /** Retries once — if this ultimately fails, the exception aborts create()'s whole transaction (rolling back the Store + Seller insert together) rather than leaving a seller with a store but no role. */
    private fun grantSellerGroup(sub: String) {
        repeat(2) { attempt ->
            try {
                cognitoClient.adminAddUserToGroup(
                    AdminAddUserToGroupRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId)
                        .username(sub)
                        .groupName("seller")
                        .build(),
                )
                return
            } catch (e: CognitoIdentityProviderException) {
                if (attempt == 1) {
                    log.error("Failed to add {} to Cognito 'seller' group after retry — aborting onboarding", sub, e)
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
        return storeRepository.save(store).toResponse()
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
            // Snapshot before mutating — this is the only way to tell a real
            // change apart from the caller simply resubmitting the same
            // values (the frontend settings form always resubmits the full
            // shape, not a diff).
            val bankDetailsChanged =
                (input.bankName != null && input.bankName != existing.bankName) ||
                    (input.bankAccountName != null && input.bankAccountName != existing.bankAccountName) ||
                    (input.bankAccountNumber != null && input.bankAccountNumber != existing.bankAccountNumber)

            input.contactEmail?.let { existing.contactEmail = it }
            input.contactPhone?.let { existing.contactPhone = it }
            input.bankAccountName?.let { existing.bankAccountName = it }
            input.bankAccountNumber?.let { existing.bankAccountNumber = it }
            input.bankName?.let { existing.bankName = it }
            input.transactionFeePercent?.let { existing.transactionFeePercent = it }
            input.codEnabled?.let { existing.codEnabled = it }
            input.onlinePaymentEnabled?.let { existing.onlinePaymentEnabled = it }
            input.bankTransferEnabled?.let { existing.bankTransferEnabled = it }
            input.sellerType?.let { existing.sellerType = wireValueOf(it) }
            input.driverLicenceNumber?.let { existing.driverLicenceNumber = it }
            if (input.abn != null) existing.abn = input.abn
            input.nicNumber?.let { existing.nicNumber = it }
            if (input.businessRegistrationNumber != null) existing.businessRegistrationNumber = input.businessRegistrationNumber
            if (input.rejectionReason != null) existing.rejectionReason = input.rejectionReason
            input.stockManagementEnabled?.let { existing.stockManagementEnabled = it }
            input.stripeEnabled?.let { existing.stripeEnabled = it }
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
            return saved.toResponse(fileStorageService)
        }

        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val platformConfig = platformConfigService.current()
        val codEnabled = input.codEnabled ?: platformConfig.defaultCodEnabled
        val onlinePaymentEnabled = input.onlinePaymentEnabled ?: platformConfig.defaultOnlinePaymentEnabled
        val bankTransferEnabled = input.bankTransferEnabled ?: platformConfig.defaultBankTransferEnabled
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
        )
        requireCountryVerificationFields(created)
        return storeSettingsRepository.save(created).toResponse(fileStorageService)
    }

    /**
     * A store's seller-identity verification fields are country-specific
     * (see StoreSettings' doc comment) — this deployment's
     * platform_settings.country_code decides which pair is required, never
     * both. The business-only field (ABN / business registration number) is
     * only required when sellerType is BUSINESS.
     */
    private fun requireCountryVerificationFields(settings: StoreSettings) {
        val countryCode = platformConfigService.current().countryCode
        if (countryCode == "LK") {
            require(!settings.nicNumber.isNullOrBlank()) { "NIC number is required" }
            if (settings.sellerType == SellerType.BUSINESS) {
                require(!settings.businessRegistrationNumber.isNullOrBlank()) {
                    "Business registration number is required for a registered business"
                }
            }
        } else {
            require(!settings.driverLicenceNumber.isNullOrBlank()) { "Driver's licence number is required" }
            if (settings.sellerType == SellerType.BUSINESS) {
                require(!settings.abn.isNullOrBlank()) { "ABN is required for a registered business" }
                require(isValidAbnChecksum(settings.abn!!)) { "Enter a valid ABN" }
            }
        }
    }

    /** POST /api/stores/{storeId}/driver-licence-document — seller uploads/replaces their driver's licence proof. */
    @Transactional
    fun uploadDriverLicenceDocument(storeId: UUID, file: MultipartFile): StoreSettingsResponse {
        requireOwnedStore(storeId)
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
        requireOwnedStore(storeId)
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
        requireOwnedStore(storeId)
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
        requireOwnedStore(storeId)
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
        return results.sortedByDescending { it.createdAt }.map { it.toResponse() }
    }

    @Transactional
    fun setVerificationStatus(storeId: UUID, input: VerificationDecisionInput): StoreResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val status = wireValueOf<StoreVerificationStatus>(input.status)
        store.verificationStatus = status
        store.isVerified = status == StoreVerificationStatus.ACTIVE
        storeRepository.save(store)

        if (status == StoreVerificationStatus.REJECTED && input.rejectionReason != null) {
            upsertSettings(storeId, StoreSettingsInput(rejectionReason = input.rejectionReason))
        }
        return store.toResponse()
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
