package com.storepilot.backend.admin

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * One row per recorded action — admin actions AND seller-initiated changes
 * to their own store — for audit purposes; write-once, never updated.
 * [targetType]/[targetId] identify what was acted on (e.g. "store"/the
 * store's UUID) when the action has a specific target; [description] is a
 * pre-rendered human-readable summary (not reconstructed from the other
 * fields at read time) so the log stays meaningful even if the target row
 * is later renamed or deleted. [actorId] is the Admin or Seller row's id,
 * whichever actually performed the action (see AuditLogService.record vs
 * recordAsSeller) — which one it is isn't stored explicitly, but is always
 * inferable from [action] (e.g. STORE_APPROVED is always an admin, STORE_
 * VERIFICATION_CHANGE_REQUESTED is always a seller).
 */
@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Column(name = "actor_email", nullable = false)
    var actorEmail: String,
    @Column(name = "actor_id")
    var actorId: UUID?,
    @Column(nullable = false)
    var action: AuditAction,
    @Column(name = "target_type")
    var targetType: String?,
    @Column(name = "target_id")
    var targetId: String?,
    @Column(nullable = false, columnDefinition = "text")
    var description: String,
) : BaseEntity()
