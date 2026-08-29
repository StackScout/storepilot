package com.storepilot.backend.notification

import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BookingReminderJobTest {
    private val bookingRepository = mockk<BookingRepository>()
    private val bookingNotifier = mockk<BookingNotifier>(relaxed = true)
    private val notificationProperties = NotificationProperties(bookingReminderBeforeHours = 24)

    private val job = BookingReminderJob(bookingRepository, bookingNotifier, notificationProperties)

    private lateinit var store: Store
    private lateinit var service: BookableService

    @BeforeEach
    fun setUp() {
        val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Studio", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        service = BookableService(store = store, name = "Haircut", slug = "haircut", description = "description", category = "handicrafts", price = 5000, durationMinutes = 30, status = ServiceStatus.ACTIVE)
            .apply { id = UUID.randomUUID() }
    }

    private fun booking() = Booking(
        bookingNumber = "BK-1001", store = store, service = service, serviceName = service.name, servicePrice = service.price,
        serviceDurationMinutes = service.durationMinutes, scheduledStart = Instant.now().plus(2, ChronoUnit.HOURS),
        scheduledEnd = Instant.now().plus(2, ChronoUnit.HOURS).plusSeconds(1800), platformFee = 175, total = 5000,
        status = BookingStatus.CONFIRMED, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
        buyerName = "Jane Buyer", buyerPhone = "+61400000002", buyerEmail = "buyer@example.com",
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `notifies and stamps every due booking`() {
        val b1 = booking()
        val b2 = booking()
        every { bookingRepository.findDueForReminder(any(), any()) } returns listOf(b1, b2)
        every { bookingRepository.saveAll(any<List<Booking>>()) } returns listOf(b1, b2)

        job.run()

        verify { bookingNotifier.bookingReminder(b1) }
        verify { bookingNotifier.bookingReminder(b2) }
        org.junit.jupiter.api.Assertions.assertNotNull(b1.lastReminderSentAt)
        org.junit.jupiter.api.Assertions.assertNotNull(b2.lastReminderSentAt)
        verify { bookingRepository.saveAll(listOf(b1, b2)) }
    }

    @Test
    fun `does nothing and does not save when there is nothing due`() {
        every { bookingRepository.findDueForReminder(any(), any()) } returns emptyList()

        job.run()

        verify(exactly = 0) { bookingNotifier.bookingReminder(any()) }
        verify(exactly = 0) { bookingRepository.saveAll(any<List<Booking>>()) }
    }
}
