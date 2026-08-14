package com.storepilot.backend.booking

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.BookingNotifier
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.storepilot.backend.stripe.StripeService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class BookingServiceTest {
    private val bookingRepository = mockk<BookingRepository>()
    private val bookableServiceRepository = mockk<BookableServiceRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val storeAvailabilityRepository = mockk<StoreAvailabilityRepository>()
    private val receiptStorageService = mockk<ReceiptStorageService>(relaxed = true)
    private val bookingNotifier = mockk<BookingNotifier>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeService = mockk<StripeService>(relaxed = true)

    private val service = BookingService(
        bookingRepository,
        bookableServiceRepository,
        storeRepository,
        storeSettingsRepository,
        storeAvailabilityRepository,
        receiptStorageService,
        bookingNotifier,
        currentActor,
        platformConfigService,
        stripeService,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller", plan = SellerPlan.PRO).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private lateinit var settings: StoreSettings
    private lateinit var bookableService: BookableService

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "test-salon",
            name = "Test Salon",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.BEAUTY,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        settings = StoreSettings(
            store = store,
            contactEmail = "store@example.com",
            contactPhone = "+61400000001",
            bankAccountName = "Test",
            bankAccountNumber = "123",
            bankName = "Test Bank",
            transactionFeePercent = BigDecimal("2.0"),
            sellerType = com.storepilot.backend.store.SellerType.INDIVIDUAL,
            bookingsEnabled = true,
        )
        bookableService = BookableService(
            store = store,
            name = "Haircut",
            slug = "haircut",
            description = "A haircut",
            category = StoreCategory.BEAUTY,
            price = 5000,
            durationMinutes = 30,
            status = ServiceStatus.ACTIVE,
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

        every { bookableServiceRepository.findById(requireNotNull(bookableService.id)) } returns Optional.of(bookableService)
        every { storeSettingsRepository.findById(storeId) } returns Optional.of(settings)
        every { storeAvailabilityRepository.findById(storeId) } returns Optional.of(StoreAvailability(store = store, leadTimeMinutes = 120))
        every { platformConfigService.current() } returns mockk<PlatformSettings> { every { countryCode } returns "AU" }
        every { currentActor.buyerOrNull() } returns null
        every { bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(any(), any(), any(), any()) } returns emptyList()
        every { bookingRepository.save(any()) } answers {
            firstArg<Booking>().apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
                updatedAt = Instant.now()
            }
        }
    }

    private fun checkoutInput(scheduledStart: Instant, paymentMethod: String = "stripe") = CheckoutBookingInput(
        storeId = storeId,
        serviceId = requireNotNull(bookableService.id),
        scheduledStart = scheduledStart,
        paymentMethod = paymentMethod,
        buyerName = "Jane Doe",
        buyerPhone = "+61400000002",
        buyerEmail = "jane@example.com",
    )

    @Test
    fun `createBooking rejects when the store hasn't enabled bookings`() {
        settings.bookingsEnabled = false
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)))
        }
    }

    @Test
    fun `createBooking rejects a slot inside the lead-time cutoff`() {
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(60 * 60)))
        }
    }

    @Test
    fun `createBooking rejects a slot that overlaps an existing booking of the same service`() {
        every { bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(any(), any(), any(), any()) } returns
            listOf(mockk<Booking>())
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)))
        }
    }

    @Test
    fun `createBooking computes platformFee from the store's transactionFeePercent, HALF_UP rounded`() {
        settings.transactionFeePercent = BigDecimal("2.5")
        val response = service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)))
        // price 5000 * 2.5% = 125.00 -> 125
        assertEquals(125, response.platformFee)
        assertEquals(5000, response.total)
        assertEquals("pending", response.status)
    }

    @Test
    fun `createBooking rejects bank-transfer for a non-Pro seller`() {
        seller.plan = SellerPlan.FREE
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60), paymentMethod = "bank-transfer"))
        }
    }

    @Test
    fun `createBooking rejects PayHere outside a Sri Lanka deployment`() {
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60), paymentMethod = "payhere"))
        }
    }

    @Test
    fun `updateStatus rejects an invalid transition`() {
        val booking = Booking(
            bookingNumber = "BK-AU-20260101-1000",
            store = store,
            service = bookableService,
            serviceName = "Haircut",
            servicePrice = 5000,
            serviceDurationMinutes = 30,
            scheduledStart = Instant.now().plusSeconds(3600),
            scheduledEnd = Instant.now().plusSeconds(5400),
            platformFee = 100,
            total = 5000,
            status = BookingStatus.CANCELLED,
            paymentMethod = PaymentMethod.STRIPE,
            paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Jane",
            buyerPhone = "+61400000002",
            buyerEmail = "jane@example.com",
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { bookingRepository.findById(requireNotNull(booking.id)) } returns Optional.of(booking)
        every { currentActor.requireSeller() } returns seller

        assertThrows(ConflictException::class.java) {
            service.updateStatus(requireNotNull(booking.id), BookingStatusUpdateInput(status = "confirmed"))
        }
    }

    @Test
    fun `updateStatus flips a COD booking to paid once completed, mirroring DELIVERED for orders`() {
        val booking = Booking(
            bookingNumber = "BK-AU-20260101-1001",
            store = store,
            service = bookableService,
            serviceName = "Haircut",
            servicePrice = 5000,
            serviceDurationMinutes = 30,
            scheduledStart = Instant.now().plusSeconds(3600),
            scheduledEnd = Instant.now().plusSeconds(5400),
            platformFee = 100,
            total = 5000,
            status = BookingStatus.CONFIRMED,
            paymentMethod = PaymentMethod.COD,
            paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Jane",
            buyerPhone = "+61400000002",
            buyerEmail = "jane@example.com",
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { bookingRepository.findById(requireNotNull(booking.id)) } returns Optional.of(booking)
        every { currentActor.requireSeller() } returns seller

        val response = service.updateStatus(requireNotNull(booking.id), BookingStatusUpdateInput(status = "completed"))

        assertEquals("completed", response.status)
        assertEquals("paid", response.paymentStatus)
    }

    @Test
    fun `cancelBooking rejects a cancellation too close to the scheduled appointment`() {
        val booking = Booking(
            bookingNumber = "BK-AU-20260101-1002",
            store = store,
            service = bookableService,
            serviceName = "Haircut",
            servicePrice = 5000,
            serviceDurationMinutes = 30,
            scheduledStart = Instant.now().plusSeconds(60 * 60),
            scheduledEnd = Instant.now().plusSeconds(60 * 90),
            platformFee = 100,
            total = 5000,
            status = BookingStatus.PENDING,
            paymentMethod = PaymentMethod.STRIPE,
            paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Jane",
            buyerPhone = "+61400000002",
            buyerEmail = "jane@example.com",
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { bookingRepository.findById(requireNotNull(booking.id)) } returns Optional.of(booking)

        assertThrows(ConflictException::class.java) {
            service.cancelBooking(requireNotNull(booking.id), CancelBookingInput(reason = "Change of plans"))
        }
    }

    @Test
    fun `cancelBooking succeeds outside the cutoff window`() {
        val booking = Booking(
            bookingNumber = "BK-AU-20260101-1003",
            store = store,
            service = bookableService,
            serviceName = "Haircut",
            servicePrice = 5000,
            serviceDurationMinutes = 30,
            scheduledStart = Instant.now().plusSeconds(3 * 60 * 60),
            scheduledEnd = Instant.now().plusSeconds(3 * 60 * 60 + 1800),
            platformFee = 100,
            total = 5000,
            status = BookingStatus.PENDING,
            paymentMethod = PaymentMethod.STRIPE,
            paymentStatus = PaymentStatus.UNPAID,
            buyerName = "Jane",
            buyerPhone = "+61400000002",
            buyerEmail = "jane@example.com",
        ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { bookingRepository.findById(requireNotNull(booking.id)) } returns Optional.of(booking)

        val response = service.cancelBooking(requireNotNull(booking.id), CancelBookingInput(reason = "Change of plans"))

        assertEquals("cancelled", response.status)
        assertEquals("Change of plans", response.cancellationReason)
    }
}
