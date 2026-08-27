package com.storepilot.backend.admin

import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.notification.EmailService
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AdminNotificationServiceTest {
    private val adminNotificationRepository = mockk<AdminNotificationRepository>()
    private val emailService = mockk<EmailService>(relaxed = true)
    private var notificationProperties = NotificationProperties(adminNotificationEmail = "admin@storepilot.au")

    private val service = AdminNotificationService(adminNotificationRepository, emailService, notificationProperties)

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "store",
            name = "Handicrafts Store",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        every { adminNotificationRepository.save(any()) } answers {
            (firstArg() as AdminNotification).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
            }
        }
    }

    private fun notification(read: Boolean = false) = AdminNotification(
        type = AdminNotificationType.BANK_DETAILS_CHANGED,
        message = "Test notification",
        storeId = store.id,
        read = read,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `summary reports the unread count`() {
        every { adminNotificationRepository.countByReadFalse() } returns 3
        assertEquals(3, service.summary().unreadCount)
    }

    @Test
    fun `markRead throws for a missing notification`() {
        val id = UUID.randomUUID()
        every { adminNotificationRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.markRead(id) }
    }

    @Test
    fun `markRead flips the notification to read`() {
        val n = notification(read = false)
        every { adminNotificationRepository.findById(n.id!!) } returns Optional.of(n)

        val result = service.markRead(n.id!!)

        assertTrue(result.read)
    }

    @Test
    fun `markAllRead flips every unread notification without touching already-read ones`() {
        val unread = notification(read = false)
        val alreadyRead = notification(read = true)
        every { adminNotificationRepository.findAllByOrderByCreatedAtDesc() } returns listOf(unread, alreadyRead)

        service.markAllRead()

        assertTrue(unread.read)
        assertTrue(alreadyRead.read)
    }

    @Test
    fun `notifyBankDetailsChanged saves a notification and emails a masked account number`() {
        service.notifyBankDetailsChanged(store, "Test Bank", "Store Account", "123456789")

        verify {
            adminNotificationRepository.save(
                match<AdminNotification> { it.type == AdminNotificationType.BANK_DETAILS_CHANGED && it.message.contains("6789") && !it.message.contains("123456789") },
            )
        }
        verify { emailService.send(to = "admin@storepilot.au", subject = any(), body = any(), attachment = any()) }
    }

    @Test
    fun `notifyBankDetailsChanged skips the email when no admin address is configured`() {
        val serviceWithNoEmail = AdminNotificationService(adminNotificationRepository, emailService, NotificationProperties(adminNotificationEmail = ""))

        serviceWithNoEmail.notifyBankDetailsChanged(store, "Test Bank", "Store Account", "123456789")

        verify(exactly = 0) { emailService.send(any(), any(), any(), any()) }
        verify { adminNotificationRepository.save(any()) }
    }

    @Test
    fun `notifyBankDetailsChanged doesn't propagate an email failure`() {
        every { emailService.send(any(), any(), any(), any()) } throws RuntimeException("SES is down")

        service.notifyBankDetailsChanged(store, "Test Bank", "Store Account", "123456789")

        verify { adminNotificationRepository.save(any()) }
    }

    @Test
    fun `notifyVerificationChangeRequested saves a notification and emails the admin`() {
        service.notifyVerificationChangeRequested(store)

        verify {
            adminNotificationRepository.save(match<AdminNotification> { it.type == AdminNotificationType.VERIFICATION_CHANGE_REQUESTED })
        }
        verify { emailService.send(to = "admin@storepilot.au", subject = any(), body = any(), attachment = any()) }
    }
}
