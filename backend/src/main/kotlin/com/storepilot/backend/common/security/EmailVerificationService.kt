package com.storepilot.backend.common.security

import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.notification.EmailService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit

private const val CODE_TTL_MINUTES = 15L
private const val MAX_ATTEMPTS = 5

/**
 * App-owned email verification — deliberately not Cognito's native
 * GetUserAttributeVerificationCode/VerifyUserAttribute pair, which need an
 * authenticated session (register() doesn't have one until this step
 * passes) plus a separate Cognito-Console email configuration nothing else
 * in this app uses. Reuses the existing EmailService transport instead, so
 * this rides the same SES/logging setup as order notifications.
 */
@Service
class EmailVerificationService(
    private val repository: EmailVerificationCodeRepository,
    private val emailService: EmailService,
    private val platformConfigService: PlatformConfigService,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun sendCode(email: String, name: String) {
        val code = "%06d".format(secureRandom.nextInt(1_000_000))
        val record = repository.findByEmail(email)
            ?: EmailVerificationCode(email = email, codeHash = "", expiresAt = Instant.now())
        record.codeHash = hash(code)
        record.expiresAt = Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)
        record.attempts = 0
        repository.save(record)

        val platformName = platformConfigService.current().name
        emailService.send(
            to = email,
            subject = "Verify your $platformName email address",
            body = buildString {
                appendLine("Hi $name,")
                appendLine()
                appendLine("Your verification code is: $code")
                appendLine()
                appendLine("This code expires in $CODE_TTL_MINUTES minutes. If you didn't request this, you can ignore this email.")
            },
        )
    }

    /**
     * Every failure path (no code on file, expired, too many wrong guesses,
     * wrong code) throws IllegalArgumentException — same "client-fixable,
     * 400" convention as password validation in AuthController.register().
     */
    @Transactional
    fun verifyCode(email: String, code: String) {
        val record = repository.findByEmail(email)
            ?: throw IllegalArgumentException("No verification code was requested for this email — request a new one")
        if (record.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Verification code expired — request a new one")
        }
        if (record.attempts >= MAX_ATTEMPTS) {
            throw IllegalArgumentException("Too many incorrect attempts — request a new code")
        }
        if (record.codeHash != hash(code)) {
            record.attempts += 1
            repository.save(record)
            throw IllegalArgumentException("Incorrect verification code")
        }
        repository.delete(record)
    }

    private fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
