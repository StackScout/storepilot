package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BuyerNotificationServiceTest {
    private val repository = mockk<BuyerNotificationRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = BuyerNotificationService(repository, currentActor)

    private lateinit var buyer: Buyer

    @BeforeEach
    fun setUp() {
        buyer = Buyer(email = "buyer@example.com", name = "Buyer").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
    }

    private fun notification(read: Boolean = false) = BuyerNotification(
        buyer = buyer, type = BuyerNotificationType.ORDER, title = "Order shipped", body = "Your order has shipped.", read = read,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `list returns a page of the current buyer's notifications`() {
        val n = notification()
        every { repository.findAllByBuyerIdOrderByCreatedAtDesc(buyer.id!!, PageRequest.of(0, 20)) } returns PageImpl(listOf(n))

        val result = service.list(0, 20)

        assertEquals(1, result.content.size)
        assertEquals(n.title, result.content.first().title)
    }

    @Test
    fun `summary reports the unread count`() {
        every { repository.countByBuyerIdAndReadFalse(buyer.id!!) } returns 2

        assertEquals(2, service.summary().unreadCount)
    }

    @Test
    fun `markRead throws for a missing notification`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.markRead(id) }
    }

    @Test
    fun `markRead rejects a notification belonging to a different buyer`() {
        val otherBuyer = Buyer(email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        val n = BuyerNotification(buyer = otherBuyer, type = BuyerNotificationType.ORDER, title = "t", body = "b").apply { id = UUID.randomUUID() }
        every { repository.findById(n.id!!) } returns Optional.of(n)

        assertThrows(ForbiddenException::class.java) { service.markRead(n.id!!) }
    }

    @Test
    fun `markRead flips the notification to read`() {
        val n = notification(read = false)
        every { repository.findById(n.id!!) } returns Optional.of(n)
        every { repository.save(n) } returns n

        val result = service.markRead(n.id!!)

        assertTrue(result.read)
    }

    @Test
    fun `markAllRead flips every unread notification without touching already-read ones`() {
        val unread = notification(read = false)
        val alreadyRead = notification(read = true)
        every { repository.findAllByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(unread, alreadyRead)

        service.markAllRead()

        assertTrue(unread.read)
        assertTrue(alreadyRead.read)
    }

    @Test
    fun `notify saves a notification for the given buyer`() {
        every { repository.save(any()) } answers { firstArg() }
        val entityId = UUID.randomUUID()

        service.notify(buyer, BuyerNotificationType.BOOKING, "Booking confirmed", "Your booking is confirmed.", entityId)

        verify {
            repository.save(
                match<BuyerNotification> {
                    it.buyer == buyer && it.type == BuyerNotificationType.BOOKING && it.title == "Booking confirmed" && it.entityId == entityId
                },
            )
        }
    }

    @Test
    fun `notify never propagates a save failure`() {
        every { repository.save(any()) } throws RuntimeException("DB is down")

        service.notify(buyer, BuyerNotificationType.ORDER, "t", "b", null)
    }
}
