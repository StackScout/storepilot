package com.storepilot.backend.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminNotificationRepository : JpaRepository<AdminNotification, UUID> {
    /** Unpaged — internal use only (markAllRead() must touch every unread row, not one page). GET /api/admin/notifications uses the paged overload below. */
    fun findAllByOrderByCreatedAtDesc(): List<AdminNotification>

    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AdminNotification>

    fun countByReadFalse(): Long
}
