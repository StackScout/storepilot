package com.islandcart.backend.store

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.wireValueOf
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

private val DISTRICT_TO_PROVINCE = mapOf(
    "Colombo" to "Western",
    "Gampaha" to "Western",
    "Kalutara" to "Western",
    "Kandy" to "Central",
    "Matale" to "Central",
    "Nuwara Eliya" to "Central",
    "Galle" to "Southern",
    "Matara" to "Southern",
    "Hambantota" to "Southern",
    "Jaffna" to "Northern",
    "Kurunegala" to "North Western",
    "Puttalam" to "North Western",
    "Anuradhapura" to "North Central",
    "Polonnaruwa" to "North Central",
    "Badulla" to "Uva",
    "Ratnapura" to "Sabaragamuwa",
    "Kegalle" to "Sabaragamuwa",
)

@Service
@Transactional(readOnly = true)
class StoreService(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
) {
    /** GET /api/stores — public marketplace listing: active stores only. */
    fun search(category: String?, query: String?, limit: Int?): List<StoreResponse> {
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

        val results = storeRepository.findAll(spec)
        return results.let { if (limit != null) it.take(limit) else it }.map { it.toResponse() }
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
        storeSettingsRepository.findById(storeId).orElse(null)?.toResponse()

    /** POST /api/stores — onboarding. Creates a new store in "pending" verification status. */
    @Transactional
    fun create(input: StoreApplicationInput): StoreResponse {
        val slug = uniqueSlug(input.name)
        val store = Store(
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
                district = input.district,
                province = DISTRICT_TO_PROVINCE[input.district] ?: input.district,
            ),
            whatsappNumber = input.whatsappNumber,
            verificationStatus = StoreVerificationStatus.PENDING,
        )
        return storeRepository.save(store).toResponse()
    }

    /**
     * PATCH /api/stores/{storeId}/settings — upserts: creates a
     * default-filled row if one doesn't exist yet, same as the frontend
     * mock (every store created via onboarding gets one; seed stores may
     * not).
     */
    @Transactional
    fun upsertSettings(storeId: UUID, input: StoreSettingsInput): StoreSettingsResponse {
        val existing = storeSettingsRepository.findById(storeId).orElse(null)
        if (existing != null) {
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
            input.nicNumber?.let { existing.nicNumber = it }
            if (input.businessRegistrationNumber != null) existing.businessRegistrationNumber = input.businessRegistrationNumber
            if (input.rejectionReason != null) existing.rejectionReason = input.rejectionReason
            requireAtLeastOnePaymentMethod(existing.codEnabled, existing.onlinePaymentEnabled, existing.bankTransferEnabled)
            return storeSettingsRepository.save(existing).toResponse()
        }

        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val codEnabled = input.codEnabled ?: true
        val onlinePaymentEnabled = input.onlinePaymentEnabled ?: true
        val bankTransferEnabled = input.bankTransferEnabled ?: false
        requireAtLeastOnePaymentMethod(codEnabled, onlinePaymentEnabled, bankTransferEnabled)
        val created = StoreSettings(
            store = store,
            contactEmail = input.contactEmail ?: "",
            contactPhone = input.contactPhone ?: "",
            bankAccountName = input.bankAccountName ?: "",
            bankAccountNumber = input.bankAccountNumber ?: "",
            bankName = input.bankName ?: "",
            transactionFeePercent = input.transactionFeePercent ?: BigDecimal("3.5"),
            codEnabled = codEnabled,
            onlinePaymentEnabled = onlinePaymentEnabled,
            bankTransferEnabled = bankTransferEnabled,
            sellerType = input.sellerType?.let { wireValueOf<SellerType>(it) } ?: SellerType.INDIVIDUAL,
            nicNumber = input.nicNumber ?: "",
            businessRegistrationNumber = input.businessRegistrationNumber,
            rejectionReason = input.rejectionReason,
        )
        return storeSettingsRepository.save(created).toResponse()
    }

    /** A store must always accept at least one payment method — otherwise checkout has nothing to offer buyers. */
    private fun requireAtLeastOnePaymentMethod(codEnabled: Boolean, onlinePaymentEnabled: Boolean, bankTransferEnabled: Boolean) {
        if (!codEnabled && !onlinePaymentEnabled && !bankTransferEnabled) {
            throw ConflictException("At least one payment method (Cash on Delivery, Online payment, or Bank transfer) must stay enabled")
        }
    }

    // --- Admin (mock, unauthenticated — see src/app/admin in the frontend) ---

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
