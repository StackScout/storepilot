package com.storepilot.backend.admin

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * One row per recorded admin action, for audit purposes — write-once, never
 * updated. [targetType]/[targetId] identify what was acted on (e.g.
 * "store"/the store's UUID) when the action has a specific target;
 * [description] is a pre-rendered human-readable summary (not reconstructed
 * from the other fields at read time) so the log stays meaningful even if
 * the target row is later renamed or deleted. [actorId] is the Admin row's
 * id when known — null is possible in principle (no caller path exists
 * today that would leave it null, since AuditLogService.record only runs
 * from within an already-admin-gated request) but isn't relied upon by any
 * query here.
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
