package com.storepilot.backend.common.security

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.Instant

/**
 * One pending verification code per email — not per Cognito user id, since
 * the Cognito user already exists (unverified) by the time this is created.
 * Re-registering or resending overwrites the row in place rather than
 * accumulating history; see EmailVerificationService.
 */
@Entity
@Table(name = "email_verification_codes")
class EmailVerificationCode(
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(name = "code_hash", nullable = false)
    var codeHash: String,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
    @Column(nullable = false)
    var attempts: Int = 0,
) : BaseEntity()
