package com.storepilot.backend.notification

import java.time.Instant
import java.util.UUID

data class BuyerNotificationResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String,
    val entityId: UUID?,
    val read: Boolean,
    val createdAt: Instant,
)

data class BuyerNotificationSummaryResponse(
    val unreadCount: Long,
)

fun BuyerNotification.toResponse(): BuyerNotificationResponse =
    BuyerNotificationResponse(
        id = requireNotNull(id),
        type = type.wireValue,
        title = title,
        body = body,
        entityId = entityId,
        read = read,
        createdAt = requireNotNull(createdAt),
    )
