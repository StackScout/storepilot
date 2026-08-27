package com.storepilot.backend.payout

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PayoutRepository : JpaRepository<Payout, UUID> {
    /** Unpaged — internal cross-service use (eligibility scans need every payout for the store to compute "already included"; SellerExportService's full data-export bundle). GET /api/stores/{storeId}/payouts uses the paged overload below. */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Payout>

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<Payout>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<Payout>

    /** Close-store precondition check — see StoreService.closeStore. */
    fun existsByStoreIdAndStatus(storeId: UUID, status: PayoutStatus): Boolean
}
