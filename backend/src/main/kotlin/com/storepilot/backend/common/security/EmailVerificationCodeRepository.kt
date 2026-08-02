package com.storepilot.backend.common.security

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EmailVerificationCodeRepository : JpaRepository<EmailVerificationCode, UUID> {
    fun findByEmail(email: String): EmailVerificationCode?
}
