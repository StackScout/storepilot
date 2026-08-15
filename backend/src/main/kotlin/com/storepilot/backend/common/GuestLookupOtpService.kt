package com.storepilot.backend.common

import com.storepilot.backend.notification.EmailService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val CODE_TTL_MINUTES = 10L
private const val MAX_ATTEMPTS = 5

/**
 * Second factor for guest order/booking lookup — an order/booking number
 * plus a phone-suffix match alone is guessable at scale (see
 * docs/roadmap.md's "Order lookup credential strength" gap); this closes
 * it by also requiring a code emailed to the order/booking's own buyer
 * email before OrderService/BookingService reveal anything. Same
 * generation/hashing/one-time-use shape as
 * common.security.EmailVerificationService, deliberately duplicated
 * rather than shared: that one is keyed by email (account verification —
 * one code per address), this one is keyed by (targetType, targetId)
 * (lookup verification — one code per order/booking, since the same
 * buyer email can have many orders in flight at once).
 */
@Service
class GuestLookupOtpService(
    private val repository: GuestLookupCodeRepository,
    private val emailService: EmailService,
    private val platformConfigService: PlatformConfigService,
) {
    private val secureRandom = SecureRandom()

    @Transactional
    fun requestCode(targetType: String, targetId: UUID, email: String, recipientName: String) {
        val code = "%06d".format(secureRandom.nextInt(1_000_000))
        val record = repository.findByTargetTypeAndTargetId(targetType, targetId)
            ?: GuestLookupCode(targetType = targetType, targetId = targetId, codeHash = "", expiresAt = Instant.now())
        record.codeHash = hash(code)
        record.expiresAt = Instant.now().plus(CODE_TTL_MINUTES, ChronoUnit.MINUTES)
        record.attempts = 0
        repository.save(record)

        val platformName = platformConfigService.current().name
        emailService.send(
            to = email,
            subject = "Your $platformName lookup code",
            body = buildString {
                appendLine("Hi $recipientName,")
                appendLine()
                appendLine("Your one-time code is: $code")
                appendLine()
                appendLine("This code expires in $CODE_TTL_MINUTES minutes. If you didn't request this, you can ignore this email.")
            },
        )
    }

    /** Every failure path throws IllegalArgumentException — same "client-fixable, 400" convention as EmailVerificationService.verifyCode. */
    @Transactional
    fun verifyCode(targetType: String, targetId: UUID, code: String) {
        val record = repository.findByTargetTypeAndTargetId(targetType, targetId)
            ?: throw IllegalArgumentException("No code was requested — request a new one")
        if (record.expiresAt.isBefore(Instant.now())) {
            throw IllegalArgumentException("Code expired — request a new one")
        }
        if (record.attempts >= MAX_ATTEMPTS) {
            throw IllegalArgumentException("Too many incorrect attempts — request a new code")
        }
        if (record.codeHash != hash(code)) {
            record.attempts += 1
            repository.save(record)
            throw IllegalArgumentException("Incorrect code")
        }
        repository.delete(record)
    }

    private fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(code.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
