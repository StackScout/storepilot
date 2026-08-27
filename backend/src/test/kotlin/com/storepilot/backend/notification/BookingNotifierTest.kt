package com.storepilot.backend.notification

import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class BookingNotifierTest {
    private val emailService = mockk<EmailService>(relaxed = true)
    private val notificationProperties = NotificationProperties(frontendBaseUrl = "https://storepilot.au")
    private val platformConfigService = mockk<PlatformConfigService>()
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>()

    private val notifier = BookingNotifier(emailService, notificationProperties, platformConfigService, pushNotificationService, pushTokenRepository)

    private lateinit var seller: Seller
    private lateinit var store: Store
    private lateinit var service: BookableService

    @BeforeEach
    fun setUp() {
        seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Studio", tagline = "tagline", description = "description",
            category = StoreCategory.HANDICRAFTS, address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        service = BookableService(store = store, name = "Haircut", slug = "haircut", description = "description", category = StoreCategory.HANDICRAFTS, price = 5000, durationMinutes = 30, status = ServiceStatus.ACTIVE)
            .apply { id = UUID.randomUUID() }
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
            currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney", returnWindowDays = 14,
        )
        every { pushTokenRepository.findBySellerId(any()) } returns emptyList()
    }

    private fun booking(cancellationReason: String? = null) = Booking(
        bookingNumber = "BK-1001", store = store, service = service, serviceName = service.name, servicePrice = service.price,
        serviceDurationMinutes = service.durationMinutes, scheduledStart = Instant.now().plus(2, ChronoUnit.DAYS),
        scheduledEnd = Instant.now().plus(2, ChronoUnit.DAYS).plusSeconds(1800), platformFee = 175, total = 5000,
        status = BookingStatus.PENDING, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID,
        buyerName = "Jane Buyer", buyerPhone = "+61400000002", buyerEmail = "buyer@example.com", cancellationReason = cancellationReason,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    @Test
    fun `sellerBookingCreated pushes every registered device`() {
        val b = booking()
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.sellerBookingCreated(b)

        verify {
            pushNotificationService.send(
                listOf("token-1"),
                match { it.contains(b.serviceName) },
                match { it.contains(b.buyerName) },
                mapOf("type" to "booking", "id" to b.id.toString()),
            )
        }
    }

    @Test
    fun `sellerBookingCreated does nothing when the seller has no devices`() {
        notifier.sellerBookingCreated(booking())
        verify(exactly = 0) { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `sellerBookingCreated swallows a push failure`() {
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        every { pushNotificationService.send(any(), any(), any(), any()) } throws RuntimeException("Expo is down")

        notifier.sellerBookingCreated(booking())
    }

    @Test
    fun `sellerBookingReminder sends a distinct push`() {
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })

        notifier.sellerBookingReminder(booking())

        verify { pushNotificationService.send(any(), match { it.startsWith("Upcoming booking") }, any(), any()) }
    }

    @Test
    fun `bookingCreated emails the buyer with the service and total`() {
        val b = booking()
        val bodySlot = slot<String>()

        notifier.bookingCreated(b)

        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains(b.bookingNumber) }, body = capture(bodySlot)) }
        assertTrue(bodySlot.captured.contains("AUD 50.00"))
    }

    @Test
    fun `bookingConfirmed emails the buyer`() {
        notifier.bookingConfirmed(booking())
        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("confirmed") }, body = any()) }
    }

    @Test
    fun `bookingCancelled includes the cancellation reason when present`() {
        val bodySlot = slot<String>()

        notifier.bookingCancelled(booking(cancellationReason = "Seller unavailable"))

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = capture(bodySlot)) }
        assertTrue(bodySlot.captured.contains("Seller unavailable"))
    }

    @Test
    fun `bookingCancelled omits the reason line when none is given`() {
        val bodySlot = slot<String>()

        notifier.bookingCancelled(booking())

        verify { emailService.send(to = "buyer@example.com", subject = any(), body = capture(bodySlot)) }
        assertTrue(!bodySlot.captured.contains("Reason:"))
    }

    @Test
    fun `bookingReminder emails the buyer`() {
        notifier.bookingReminder(booking())
        verify { emailService.send(to = "buyer@example.com", subject = match { it.contains("Reminder") }, body = any()) }
    }

    @Test
    fun `an email send failure never propagates`() {
        every { emailService.send(any(), any(), any()) } throws RuntimeException("SES is down")
        notifier.bookingReminder(booking())
    }
}
