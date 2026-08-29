package com.storepilot.backend.notification

import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class SellerBookingReminderJobTest {
    private val bookingRepository = mockk<BookingRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val bookingNotifier = mockk<BookingNotifier>(relaxed = true)

    private val job = SellerBookingReminderJob(bookingRepository, storeSettingsRepository, bookingNotifier)

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

    private fun booking(scheduledStart: Instant) = Booking(
        bookingNumber = "BK-1001", store = store, service = service, serviceName = service.name, servicePrice = service.price,
        serviceDurationMinutes = service.durationMinutes, scheduledStart = scheduledStart, scheduledEnd = scheduledStart.plusSeconds(1800),
        platformFee = 175, total = 5000, status = BookingStatus.CONFIRMED, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
        buyerName = "Jane Buyer", buyerPhone = "+61400000002", buyerEmail = "buyer@example.com",
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    private fun storeSettings(minutesBefore: Int) = StoreSettings(
        store = store, contactEmail = "contact@store.example.com", contactPhone = "+61400000001",
        bankAccountName = "Store Account", bankAccountNumber = "123456789", bankName = "Test Bank",
        transactionFeePercent = BigDecimal("3.5"), sellerType = SellerType.INDIVIDUAL, sellerBookingReminderMinutesBefore = minutesBefore,
    )

    @Test
    fun `reminds a booking once inside the store's own configured lead time`() {
        val b = booking(Instant.now().plus(30, ChronoUnit.MINUTES))
        every { bookingRepository.findCandidatesForSellerReminder(any(), any()) } returns listOf(b)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings(minutesBefore = 60))
        every { bookingRepository.saveAll(any<List<Booking>>()) } returns listOf(b)

        job.run()

        verify { bookingNotifier.sellerBookingReminder(b) }
        assertNotNull(b.sellerReminderSentAt)
        verify { bookingRepository.saveAll(listOf(b)) }
    }

    @Test
    fun `does not remind a booking still outside the store's configured lead time`() {
        val b = booking(Instant.now().plus(90, ChronoUnit.MINUTES))
        every { bookingRepository.findCandidatesForSellerReminder(any(), any()) } returns listOf(b)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings(minutesBefore = 60))

        job.run()

        verify(exactly = 0) { bookingNotifier.sellerBookingReminder(any()) }
        assertNull(b.sellerReminderSentAt)
        verify(exactly = 0) { bookingRepository.saveAll(any<List<Booking>>()) }
    }

    @Test
    fun `falls back to a 60-minute default when the store has no settings row`() {
        val b = booking(Instant.now().plus(30, ChronoUnit.MINUTES))
        every { bookingRepository.findCandidatesForSellerReminder(any(), any()) } returns listOf(b)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.empty()
        every { bookingRepository.saveAll(any<List<Booking>>()) } returns listOf(b)

        job.run()

        verify { bookingNotifier.sellerBookingReminder(b) }
    }
}
