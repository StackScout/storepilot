package com.storepilot.backend.common

import com.storepilot.backend.notification.EmailService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class GuestLookupOtpServiceTest {
    private val repository = mockk<GuestLookupCodeRepository>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private val platformConfigService = mockk<PlatformConfigService>()

    private val service = GuestLookupOtpService(repository, emailService, platformConfigService)

    private val targetId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
            currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true, proPlanEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney", returnWindowDays = 14,
        )
        every { repository.save(any()) } answers { firstArg() }
    }

    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun `requestCode creates a new record and emails a code when none exists yet`() {
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns null
        val recordSlot = slot<GuestLookupCode>()

        service.requestCode("order", targetId, "buyer@example.com", "Jane")

        verify { repository.save(capture(recordSlot)) }
        assertEquals("order", recordSlot.captured.targetType)
        assertEquals(targetId, recordSlot.captured.targetId)
        assertEquals(0, recordSlot.captured.attempts)
        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("lookup code") }, body = any()) }
    }

    @Test
    fun `requestCode overwrites an existing record and resets its attempt count`() {
        val existing = GuestLookupCode(targetType = "order", targetId = targetId, codeHash = "stale-hash", expiresAt = Instant.now().minus(1, ChronoUnit.DAYS), attempts = 3)
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns existing

        service.requestCode("order", targetId, "buyer@example.com", "Jane")

        assertEquals(0, existing.attempts)
        assertNotEquals("stale-hash", existing.codeHash)
        verify { repository.save(existing) }
    }

    @Test
    fun `requestCode never propagates an email failure`() {
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns null
        every { emailService.send(any(), any(), any()) } throws RuntimeException("SES is down")

        service.requestCode("order", targetId, "buyer@example.com", "Jane")

        verify { repository.save(any()) }
    }

    @Test
    fun `verifyCode rejects when no code was ever requested`() {
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns null

        val ex = assertThrows(IllegalArgumentException::class.java) { service.verifyCode("order", targetId, "123456") }
        assertEquals("No code was requested — request a new one", ex.message)
    }

    @Test
    fun `verifyCode rejects an expired code`() {
        val record = GuestLookupCode(targetType = "order", targetId = targetId, codeHash = sha256("123456"), expiresAt = Instant.now().minus(1, ChronoUnit.MINUTES))
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns record

        val ex = assertThrows(IllegalArgumentException::class.java) { service.verifyCode("order", targetId, "123456") }
        assertEquals("Code expired — request a new one", ex.message)
    }

    @Test
    fun `verifyCode rejects once too many attempts have already been made`() {
        val record = GuestLookupCode(targetType = "order", targetId = targetId, codeHash = sha256("123456"), expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES), attempts = 5)
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns record

        val ex = assertThrows(IllegalArgumentException::class.java) { service.verifyCode("order", targetId, "123456") }
        assertEquals("Too many incorrect attempts — request a new code", ex.message)
    }

    @Test
    fun `verifyCode increments attempts and rejects a wrong code`() {
        val record = GuestLookupCode(targetType = "order", targetId = targetId, codeHash = sha256("123456"), expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES), attempts = 1)
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns record

        val ex = assertThrows(IllegalArgumentException::class.java) { service.verifyCode("order", targetId, "000000") }

        assertEquals("Incorrect code", ex.message)
        assertEquals(2, record.attempts)
        verify { repository.save(record) }
    }

    @Test
    fun `verifyCode deletes the record on a correct code`() {
        val record = GuestLookupCode(targetType = "order", targetId = targetId, codeHash = sha256("123456"), expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES))
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { repository.findByTargetTypeAndTargetId("order", targetId) } returns record
        every { repository.delete(record) } returns Unit

        service.verifyCode("order", targetId, "123456")

        verify { repository.delete(record) }
    }
}
