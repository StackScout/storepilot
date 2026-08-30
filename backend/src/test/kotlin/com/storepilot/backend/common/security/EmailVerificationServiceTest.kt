package com.storepilot.backend.common.security

import com.storepilot.backend.common.EmailDeliveryException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.notification.EmailService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

class EmailVerificationServiceTest {
    private val repository = mockk<EmailVerificationCodeRepository>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private val platformConfigService = mockk<PlatformConfigService>()

    private val service = EmailVerificationService(repository, emailService, platformConfigService)

    @BeforeEach
    fun setUp() {
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot",
            tagline = "tagline",
            countryName = "Australia",
            countryCode = "AU",
            currencyCode = "AUD",
            currencySymbol = "$",
            currencyLocale = "en-AU",
            platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000,
            proMonthlyPriceCents = 2900,
            defaultCodEnabled = true,
            defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true,
            proPlanEnabled = true,
            supportEmail = "hello@storepilot.au",
            companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney",
            returnWindowDays = 14,
        )
        every { repository.save(any()) } answers { firstArg() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ---- sendCode ----

    @Test
    fun `sendCode creates a new record when none exists yet`() {
        every { repository.findByEmail("buyer@example.com") } returns null
        val slot = slot<EmailVerificationCode>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        service.sendCode("buyer@example.com", "Jane")

        assertEquals("buyer@example.com", slot.captured.email)
        assertEquals(0, slot.captured.attempts)
        verify { emailService.send(to = "buyer@example.com", subject = any(), body = any(), attachment = null) }
    }

    @Test
    fun `sendCode overwrites an existing record and resets attempts`() {
        val existing = EmailVerificationCode(email = "buyer@example.com", codeHash = "stale-hash", expiresAt = Instant.now().minusSeconds(3600)).apply { attempts = 3 }
        every { repository.findByEmail("buyer@example.com") } returns existing
        every { repository.save(any()) } answers { firstArg() }

        service.sendCode("buyer@example.com", "Jane")

        assertEquals(0, existing.attempts)
        assertTrue(existing.expiresAt.isAfter(Instant.now()))
    }

    @Test
    fun `sendCode wraps an email delivery failure`() {
        every { repository.findByEmail(any()) } returns null
        every { emailService.send(any(), any(), any(), any()) } throws RuntimeException("SES is down")

        assertThrows(EmailDeliveryException::class.java) { service.sendCode("buyer@example.com", "Jane") }
    }

    // ---- verifyCode ----

    @Test
    fun `verifyCode throws when no code was ever requested`() {
        every { repository.findByEmail("buyer@example.com") } returns null
        assertThrows(IllegalArgumentException::class.java) { service.verifyCode("buyer@example.com", "123456") }
    }

    @Test
    fun `verifyCode throws when the code has expired`() {
        val record = EmailVerificationCode(email = "buyer@example.com", codeHash = sha256("123456"), expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES))
        every { repository.findByEmail("buyer@example.com") } returns record

        assertThrows(IllegalArgumentException::class.java) { service.verifyCode("buyer@example.com", "123456") }
    }

    @Test
    fun `verifyCode throws after too many incorrect attempts`() {
        val record = EmailVerificationCode(email = "buyer@example.com", codeHash = sha256("123456"), expiresAt = Instant.now().plusSeconds(600)).apply { attempts = 5 }
        every { repository.findByEmail("buyer@example.com") } returns record

        assertThrows(IllegalArgumentException::class.java) { service.verifyCode("buyer@example.com", "123456") }
    }

    @Test
    fun `verifyCode increments attempts on a wrong code`() {
        val record = EmailVerificationCode(email = "buyer@example.com", codeHash = sha256("123456"), expiresAt = Instant.now().plusSeconds(600))
        every { repository.findByEmail("buyer@example.com") } returns record

        assertThrows(IllegalArgumentException::class.java) { service.verifyCode("buyer@example.com", "000000") }

        assertEquals(1, record.attempts)
        verify { repository.save(record) }
    }

    @Test
    fun `verifyCode deletes the record once verified successfully`() {
        val record = EmailVerificationCode(email = "buyer@example.com", codeHash = sha256("123456"), expiresAt = Instant.now().plusSeconds(600))
        every { repository.findByEmail("buyer@example.com") } returns record
        every { repository.delete(record) } returns Unit

        service.verifyCode("buyer@example.com", "123456")

        verify { repository.delete(record) }
    }
}
