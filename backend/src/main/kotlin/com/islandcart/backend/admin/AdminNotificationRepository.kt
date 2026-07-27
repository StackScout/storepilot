package com.islandcart.backend.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminNotificationRepository : JpaRepository<AdminNotification, UUID> {
    fun findAllByOrderByCreatedAtDesc(): List<AdminNotification>
    fun countByReadFalse(): Long
}
