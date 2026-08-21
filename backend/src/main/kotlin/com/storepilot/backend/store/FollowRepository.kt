package com.storepilot.backend.store

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FollowRepository : JpaRepository<Follow, UUID> {
    fun findByBuyerIdAndStoreId(buyerId: UUID, storeId: UUID): Follow?

    fun existsByBuyerIdAndStoreId(buyerId: UUID, storeId: UUID): Boolean

    /** Buyer-account-deletion sweep — see BuyerAccountService. */
    fun findByBuyerId(buyerId: UUID): List<Follow>
}
