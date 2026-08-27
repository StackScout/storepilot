package com.storepilot.backend.payout

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FeeCollectionRepository : JpaRepository<FeeCollection, UUID> {
    /** Unpaged — internal cross-service use (eligibility scans need every fee collection for the store to compute "already included"; SellerExportService's full data-export bundle). GET /api/stores/{storeId}/fee-collections uses the paged overload below. */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<FeeCollection>

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<FeeCollection>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<FeeCollection>

    /** Close-store precondition check — see StoreService.closeStore. */
    fun existsByStoreIdAndStatus(storeId: UUID, status: FeeCollectionStatus): Boolean
}
