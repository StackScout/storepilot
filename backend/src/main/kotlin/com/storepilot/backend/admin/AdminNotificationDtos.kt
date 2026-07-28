package com.storepilot.backend.admin

import java.time.Instant
import java.util.UUID

data class AdminNotificationResponse(
    val id: UUID,
    val type: String,
    val message: String,
    val storeId: UUID?,
    val read: Boolean,
    val createdAt: Instant,
)

data class AdminNotificationSummaryResponse(
    val unreadCount: Long,
)

fun AdminNotification.toResponse(): AdminNotificationResponse =
    AdminNotificationResponse(
        id = requireNotNull(id),
        type = type.wireValue,
        message = message,
        storeId = storeId,
        read = read,
        createdAt = requireNotNull(createdAt),
    )
