package com.storepilot.backend.payout

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeeCollectionRepository : JpaRepository<FeeCollection, UUID> {
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<FeeCollection>

    /** Close-store precondition check — see StoreService.closeStore. */
    fun existsByStoreIdAndStatus(storeId: UUID, status: FeeCollectionStatus): Boolean
}
