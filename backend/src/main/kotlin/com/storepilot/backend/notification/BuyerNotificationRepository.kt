package com.storepilot.backend.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuyerNotificationRepository : JpaRepository<BuyerNotification, UUID> {
    /** Unpaged — internal use only (markAllRead() must touch every unread row, not one page). GET /api/me/buyer/notifications uses the paged overload below. */
    fun findAllByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<BuyerNotification>

    fun findAllByBuyerIdOrderByCreatedAtDesc(buyerId: UUID, pageable: Pageable): Page<BuyerNotification>

    fun countByBuyerIdAndReadFalse(buyerId: UUID): Long
}
