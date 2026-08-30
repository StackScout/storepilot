package com.storepilot.backend.notification

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SellerNotificationRepository : JpaRepository<SellerNotification, UUID> {
    /** Unpaged — internal use only (markAllRead() must touch every unread row, not one page). GET /api/me/seller/notifications uses the paged overload below. */
    fun findAllBySellerIdOrderByCreatedAtDesc(sellerId: UUID): List<SellerNotification>

    fun findAllBySellerIdOrderByCreatedAtDesc(sellerId: UUID, pageable: Pageable): Page<SellerNotification>

    fun countBySellerIdAndReadFalse(sellerId: UUID): Long
}
