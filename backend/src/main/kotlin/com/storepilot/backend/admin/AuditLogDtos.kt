package com.storepilot.backend.admin

import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val actorEmail: String,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val description: String,
    val createdAt: Instant,
)

fun AuditLog.toResponse() = AuditLogResponse(
    id = requireNotNull(id),
    actorEmail = actorEmail,
    action = action.wireValue,
    targetType = targetType,
    targetId = targetId,
    description = description,
    createdAt = requireNotNull(createdAt),
)
