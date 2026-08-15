package com.storepilot.backend.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One pending one-time code per (targetType, targetId) — e.g. one order,
 * one booking — proving the requester can read the mail sent to that
 * order/booking's own buyer email before GuestLookupOtpService.verifyCode
 * reveals its details to a guest. Requesting a new code overwrites the row
 * in place, same "no history" convention as
 * common.security.EmailVerificationCode. `targetType` is a plain string
 * ("order"/"booking"), not a WireValue enum — this table is never
 * serialized to JSON, so the wire-format-stability concern that pattern
 * exists for doesn't apply here.
 */
@Entity
@Table(
    name = "guest_lookup_codes",
    uniqueConstraints = [UniqueConstraint(columnNames = ["target_type", "target_id"])],
)
class GuestLookupCode(
    @Column(name = "target_type", nullable = false)
    var targetType: String,
    @Column(name = "target_id", nullable = false)
    var targetId: UUID,
    @Column(name = "code_hash", nullable = false)
    var codeHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(nullable = false)
    var attempts: Int = 0,
) : BaseEntity()
