package com.storepilot.backend.store

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface StoreRepository : JpaRepository<Store, UUID>, JpaSpecificationExecutor<Store> {
    fun findBySlug(slug: String): Store?

    fun findByVerificationStatus(status: StoreVerificationStatus): List<Store>

    fun findBySellerId(sellerId: UUID): Store?

    /** Guards CategoryController's delete — see its doc comment. */
    fun existsByCategory(category: String): Boolean
}

interface StoreSettingsRepository : JpaRepository<StoreSettings, UUID> {
    /** Looked up from the Stripe webhook's account.updated event — see StripeConnectService. */
    fun findByStripeAccountId(stripeAccountId: String): StoreSettings?
}

interface StoreVerificationChangeRequestRepository : JpaRepository<StoreVerificationChangeRequest, UUID> {
    fun findByStoreIdAndStatus(storeId: UUID, status: StoreVerificationChangeRequestStatus): StoreVerificationChangeRequest?

    fun findByStatusOrderByCreatedAtDesc(status: StoreVerificationChangeRequestStatus): List<StoreVerificationChangeRequest>

    fun findAllByOrderByCreatedAtDesc(): List<StoreVerificationChangeRequest>
}
