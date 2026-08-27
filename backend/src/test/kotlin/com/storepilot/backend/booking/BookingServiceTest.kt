package com.storepilot.backend.booking

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.coupon.CouponKind
import com.storepilot.backend.coupon.CouponResolution
import com.storepilot.backend.coupon.CouponService
import com.storepilot.backend.common.GuestLookupOtpService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.sse.SseHub
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
import io.mockk.verify
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
    private val guestLookupOtpService = mockk<GuestLookupOtpService>(relaxed = true)
    private val sseHub = mockk<SseHub>(relaxed = true)
    private val couponService = mockk<CouponService>()

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
        guestLookupOtpService,
        sseHub,
        couponService,
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
        every { bookingRepository.saveAll(any<List<Booking>>()) } answers {
            firstArg<List<Booking>>().map {
                it.apply {
                    if (id == null) id = UUID.randomUUID()
                    if (createdAt == null) createdAt = Instant.now()
                    updatedAt = Instant.now()
                }
            }
        }
    }

    private fun checkoutInput(scheduledStart: Instant, paymentMethod: String = "stripe", occurrenceCount: Int? = null) = CheckoutBookingInput(
        storeId = storeId,
        serviceId = requireNotNull(bookableService.id),
        scheduledStart = scheduledStart,
        paymentMethod = paymentMethod,
        buyerName = "Jane Doe",
        buyerPhone = "+61400000002",
        buyerEmail = "jane@example.com",
        occurrenceCount = occurrenceCount,
    )

    private fun booking(
        status: BookingStatus = BookingStatus.PENDING,
        paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
        scheduledStart: Instant = Instant.now().plusSeconds(3 * 60 * 60),
        buyer: Buyer? = null,
    ) = Booking(
        bookingNumber = "BK-AU-20260101-1000", store = store, service = bookableService, serviceName = "Haircut", servicePrice = 5000,
        serviceDurationMinutes = 30, scheduledStart = scheduledStart, scheduledEnd = scheduledStart.plusSeconds(1800),
        platformFee = 100, total = 5000, status = status, paymentMethod = paymentMethod, paymentStatus = paymentStatus,
        buyerName = "Jane", buyerPhone = "+61400000002", buyerEmail = "jane@example.com", buyer = buyer,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

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
    fun `createBooking rejects a recurring series for an online payment method`() {
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60), paymentMethod = "stripe", occurrenceCount = 4))
        }
    }

    @Test
    fun `createBooking rejects a recurring series above the max occurrence count`() {
        assertThrows(ConflictException::class.java) {
            service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60), paymentMethod = "cod", occurrenceCount = 13))
        }
    }

    @Test
    fun `createBooking creates one weekly-spaced occurrence per count, sharing a recurrenceGroupId`() {
        var created: List<Booking> = emptyList()
        every { bookingRepository.saveAll(any<List<Booking>>()) } answers {
            firstArg<List<Booking>>().map {
                it.apply {
                    if (id == null) id = UUID.randomUUID()
                    if (createdAt == null) createdAt = Instant.now()
                    updatedAt = Instant.now()
                }
            }.also { created = it }
        }

        val start = Instant.now().plusSeconds(3 * 60 * 60)
        val response = service.createBooking(checkoutInput(start, paymentMethod = "cod", occurrenceCount = 3))

        assertEquals(3, created.size)
        assertEquals(1, created.map { it.recurrenceGroupId }.toSet().size)
        assertEquals(created[0].recurrenceGroupId, response.recurrenceGroupId)
        assertEquals(start, created[0].scheduledStart)
        assertEquals(start.plusSeconds(7 * 24 * 60 * 60), created[1].scheduledStart)
        assertEquals(start.plusSeconds(14 * 24 * 60 * 60), created[2].scheduledStart)
    }

    @Test
    fun `createBooking leaves recurrenceGroupId null for a one-off booking`() {
        val response = service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)))
        assertEquals(null, response.recurrenceGroupId)
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

    @Test
    fun `createBooking applies a resolved coupon's discount and records its use`() {
        every { couponService.resolve("SAVE10", storeId, CouponKind.BOOKING, 5000) } returns CouponResolution(couponId = UUID.randomUUID(), code = "SAVE10", discountAmount = 500)
        every { couponService.recordUse(any()) } returns Unit
        val input = checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)).copy(couponCode = "SAVE10")

        val response = service.createBooking(input)

        assertEquals(4500, response.total)
        assertEquals("SAVE10", response.couponCode)
        verify { couponService.recordUse(any()) }
    }

    @Test
    fun `createBooking works with no coupon code`() {
        val response = service.createBooking(checkoutInput(Instant.now().plusSeconds(3 * 60 * 60)))
        assertEquals(null, response.couponCode)
        verify(exactly = 0) { couponService.recordUse(any()) }
    }

    @Test
    fun `listByStore rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        every { storeRepository.findById(storeId) } returns Optional.of(store)

        assertThrows(ForbiddenException::class.java) { service.listByStore(storeId, null) }
    }

    @Test
    fun `listByStore filters by status when provided`() {
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(booking(status = BookingStatus.PENDING), booking(status = BookingStatus.CONFIRMED))

        val result = service.listByStore(storeId, "confirmed")

        assertEquals(1, result.size)
        assertEquals("confirmed", result.first().status)
    }

    @Test
    fun `listByStore returns everything when no status filter is given`() {
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(booking(status = BookingStatus.PENDING), booking(status = BookingStatus.CONFIRMED))

        assertEquals(2, service.listByStore(storeId, null).size)
    }

    @Test
    fun `listByCurrentBuyer lists the current buyer's bookings`() {
        val buyer = Buyer(name = "Jane", email = "jane@example.com").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { bookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyer.id!!) } returns listOf(booking())

        assertEquals(1, service.listByCurrentBuyer().size)
    }

    @Test
    fun `getById throws for a missing booking`() {
        val id = UUID.randomUUID()
        every { bookingRepository.findById(id) } returns Optional.empty()

        assertThrows(NotFoundException::class.java) { service.getById(id) }
    }

    @Test
    fun `getById returns the mapped booking`() {
        val b = booking()
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        assertEquals(b.id, service.getById(b.id!!).id)
    }

    @Test
    fun `findEntity returns null for a missing booking`() {
        val id = UUID.randomUUID()
        every { bookingRepository.findById(id) } returns Optional.empty()

        assertEquals(null, service.findEntity(id))
    }

    @Test
    fun `requestLookupCode is a no-op when the booking number and phone don't match`() {
        every { bookingRepository.findByBookingNumberIgnoreCase("BK-AU-20260101-1000") } returns null

        service.requestLookupCode("BK-AU-20260101-1000", "0400000002")

        verify(exactly = 0) { guestLookupOtpService.requestCode(any(), any(), any(), any()) }
    }

    @Test
    fun `requestLookupCode requests a code when the phone suffix matches`() {
        val b = booking()
        every { bookingRepository.findByBookingNumberIgnoreCase(b.bookingNumber) } returns b

        service.requestLookupCode(b.bookingNumber, "0400000002")

        verify { guestLookupOtpService.requestCode("booking", b.id!!, b.buyerEmail, b.buyerName) }
    }

    @Test
    fun `verifyLookupCode throws not-found when the booking number and phone don't match`() {
        every { bookingRepository.findByBookingNumberIgnoreCase("BK-AU-20260101-1000") } returns null

        assertThrows(NotFoundException::class.java) { service.verifyLookupCode("BK-AU-20260101-1000", "0400000002", "123456") }
    }

    @Test
    fun `verifyLookupCode returns the booking once the code checks out`() {
        val b = booking()
        every { bookingRepository.findByBookingNumberIgnoreCase(b.bookingNumber) } returns b
        every { guestLookupOtpService.verifyCode("booking", b.id!!, "123456") } returns Unit

        val result = service.verifyLookupCode(b.bookingNumber, "0400000002", "123456")

        assertEquals(b.id, result.id)
    }

    @Test
    fun `listByRecurrenceGroup lists every occurrence`() {
        val groupId = UUID.randomUUID()
        every { bookingRepository.findByRecurrenceGroupIdOrderByScheduledStartAsc(groupId) } returns listOf(booking(), booking())

        assertEquals(2, service.listByRecurrenceGroup(groupId).size)
    }

    @Test
    fun `updateStatus rejects a non-owning seller`() {
        val b = booking(status = BookingStatus.CONFIRMED)
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.updateStatus(b.id!!, BookingStatusUpdateInput(status = "completed")) }
    }

    @Test
    fun `updateStatus refunds a paid Stripe booking on cancellation`() {
        val b = booking(status = BookingStatus.CONFIRMED, paymentStatus = PaymentStatus.PAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns seller

        val response = service.updateStatus(b.id!!, BookingStatusUpdateInput(status = "cancelled"))

        assertEquals("refunded", response.paymentStatus)
        verify { stripeService.refundBookingPayment(b) }
    }

    @Test
    fun `uploadReceipt rejects a non-bank-transfer booking`() {
        val b = booking(paymentMethod = PaymentMethod.STRIPE)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        assertThrows(ConflictException::class.java) { service.uploadReceipt(b.id!!, mockk(relaxed = true)) }
    }

    @Test
    fun `uploadReceipt rejects a booking that's already paid`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        assertThrows(ConflictException::class.java) { service.uploadReceipt(b.id!!, mockk(relaxed = true)) }
    }

    @Test
    fun `uploadReceipt stores the receipt and adds a timeline entry`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.UNPAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { receiptStorageService.store(any()) } returns "receipts/receipt.jpg"

        service.uploadReceipt(b.id!!, mockk(relaxed = true))

        assertEquals("receipts/receipt.jpg", b.receiptUrl)
        assertEquals(1, b.timeline.size)
    }

    @Test
    fun `verifyBankTransfer rejects a non-owning seller`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER)
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(ForbiddenException::class.java) { service.verifyBankTransfer(b.id!!, VerifyBookingBankTransferInput(approved = true, note = null)) }
    }

    @Test
    fun `verifyBankTransfer rejects a non-bank-transfer booking`() {
        val b = booking(paymentMethod = PaymentMethod.STRIPE)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns seller

        assertThrows(ConflictException::class.java) { service.verifyBankTransfer(b.id!!, VerifyBookingBankTransferInput(approved = true, note = null)) }
    }

    @Test
    fun `verifyBankTransfer rejects a booking that's already been decided`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.PAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns seller

        assertThrows(ConflictException::class.java) { service.verifyBankTransfer(b.id!!, VerifyBookingBankTransferInput(approved = true, note = null)) }
    }

    @Test
    fun `verifyBankTransfer marks paid and confirms a pending booking on approval`() {
        val b = booking(status = BookingStatus.PENDING, paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.UNPAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns seller

        val response = service.verifyBankTransfer(b.id!!, VerifyBookingBankTransferInput(approved = true, note = "Confirmed"))

        assertEquals("paid", response.paymentStatus)
        assertEquals("confirmed", response.status)
    }

    @Test
    fun `verifyBankTransfer clears the receipt on rejection`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER, paymentStatus = PaymentStatus.UNPAID).apply { receiptUrl = "receipts/receipt.jpg" }
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { currentActor.requireSeller() } returns seller

        val response = service.verifyBankTransfer(b.id!!, VerifyBookingBankTransferInput(approved = false, note = "Doesn't match"))

        assertEquals(null, b.receiptUrl)
        assertEquals("unpaid", response.paymentStatus)
    }

    @Test
    fun `cancelBooking rejects a booking that's already completed`() {
        val b = booking(status = BookingStatus.COMPLETED)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        assertThrows(ConflictException::class.java) { service.cancelBooking(b.id!!, CancelBookingInput(reason = "Change of plans")) }
    }

    @Test
    fun `cancelBooking refunds a paid Stripe booking`() {
        val b = booking(status = BookingStatus.CONFIRMED, paymentMethod = PaymentMethod.STRIPE, paymentStatus = PaymentStatus.PAID, scheduledStart = Instant.now().plusSeconds(3 * 60 * 60))
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        val response = service.cancelBooking(b.id!!, CancelBookingInput(reason = "Change of plans"))

        assertEquals("refunded", response.paymentStatus)
        verify { stripeService.refundBookingPayment(b) }
    }
}
