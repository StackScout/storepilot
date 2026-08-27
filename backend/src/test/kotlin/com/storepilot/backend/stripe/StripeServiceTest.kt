package com.storepilot.backend.stripe

import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.SellerType
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.stripe.model.Refund
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

class StripeServiceTest {
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val storeSettingsRepository = mockk<StoreSettingsRepository>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeProperties = StripeProperties(
        successUrlBase = "http://localhost:3000/orders",
        cancelUrlBase = "http://localhost:3000/orders",
        bookingSuccessUrlBase = "http://localhost:3000/bookings",
        bookingCancelUrlBase = "http://localhost:3000/bookings",
    )

    private val service = StripeService(orderRepository, bookingRepository, storeSettingsRepository, platformConfigService, stripeProperties)

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "store",
            name = "Store",
            tagline = "tagline",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }

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
            supportEmail = "hello@storepilot.au",
            companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney",
            returnWindowDays = 14,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun storeSettings(accountId: String? = "acct_123", chargesEnabled: Boolean = true) = StoreSettings(
        store = store,
        contactEmail = "store@example.com",
        contactPhone = "+61400000001",
        bankAccountName = "Store",
        bankAccountNumber = "12345678",
        bankName = "Test Bank",
        transactionFeePercent = BigDecimal("5.0"),
        sellerType = SellerType.INDIVIDUAL,
        stripeAccountId = accountId,
        stripeChargesEnabled = chargesEnabled,
    ).apply { id = store.id }

    private fun order(
        id: UUID = UUID.randomUUID(),
        total: Int = 10000,
        platformFee: Int = 350,
        paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
        status: OrderStatus = OrderStatus.PENDING,
        stripePaymentIntentId: String? = null,
    ) = Order(
        orderNumber = "AU-20260101-1234",
        store = store,
        subtotal = total,
        shippingFee = 0,
        platformFee = platformFee,
        total = total,
        status = status,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        shipping = ShippingDetails(fullName = "Jane Doe", phone = "0400000000"),
        buyerEmail = "buyer@example.com",
        fulfillmentTimeHours = 48,
        deliveryTimeHours = 120,
        stripePaymentIntentId = stripePaymentIntentId,
    ).apply { this.id = id }

    private fun booking(
        id: UUID = UUID.randomUUID(),
        total: Int = 5000,
        paymentMethod: PaymentMethod = PaymentMethod.STRIPE,
        paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
        status: BookingStatus = BookingStatus.PENDING,
        stripePaymentIntentId: String? = null,
    ): Booking {
        val bookableService = BookableService(
            store = store,
            name = "A service",
            slug = "a-service",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            price = total,
            durationMinutes = 60,
            status = ServiceStatus.ACTIVE,
        ).apply { this.id = UUID.randomUUID() }
        return Booking(
            bookingNumber = "AU-20260101-5678",
            store = store,
            service = bookableService,
            serviceName = "A service",
            servicePrice = total,
            serviceDurationMinutes = 60,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(3600),
            platformFee = 175,
            total = total,
            status = status,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            buyerName = "John Smith",
            buyerPhone = "0400000000",
            buyerEmail = "buyer@example.com",
            stripePaymentIntentId = stripePaymentIntentId,
        ).apply { this.id = id }
    }

    // ---- createCheckoutSession ----

    @Test
    fun `createCheckoutSession throws for a missing order`() {
        val id = UUID.randomUUID()
        every { orderRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.createCheckoutSession(id) }
    }

    @Test
    fun `createCheckoutSession rejects an order that isn't a Stripe payment`() {
        val o = order(paymentMethod = PaymentMethod.COD)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        assertThrows(ConflictException::class.java) { service.createCheckoutSession(o.id!!) }
    }

    @Test
    fun `createCheckoutSession rejects an order that's already paid`() {
        val o = order(paymentStatus = PaymentStatus.PAID)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        assertThrows(ConflictException::class.java) { service.createCheckoutSession(o.id!!) }
    }

    @Test
    fun `createCheckoutSession rejects a store with no connected Stripe account`() {
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings(accountId = null))
        assertThrows(ConflictException::class.java) { service.createCheckoutSession(o.id!!) }
    }

    @Test
    fun `createCheckoutSession rejects a store whose Stripe charges aren't enabled yet`() {
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings(chargesEnabled = false))
        assertThrows(ConflictException::class.java) { service.createCheckoutSession(o.id!!) }
    }

    @Test
    fun `createCheckoutSession returns the Stripe-hosted checkout URL`() {
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())

        mockkStatic(Session::class)
        val fakeSession = mockk<Session> { every { url } returns "https://checkout.stripe.com/session-abc" }
        every { Session.create(any<SessionCreateParams>(), any<RequestOptions>()) } returns fakeSession

        val result = service.createCheckoutSession(o.id!!)

        assertEquals("https://checkout.stripe.com/session-abc", result.checkoutUrl)
    }

    // ---- createBookingCheckoutSession ----

    @Test
    fun `createBookingCheckoutSession throws for a missing booking`() {
        val id = UUID.randomUUID()
        every { bookingRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.createBookingCheckoutSession(id) }
    }

    @Test
    fun `createBookingCheckoutSession rejects a booking that's already paid`() {
        val b = booking(paymentStatus = PaymentStatus.PAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        assertThrows(ConflictException::class.java) { service.createBookingCheckoutSession(b.id!!) }
    }

    @Test
    fun `createBookingCheckoutSession returns the Stripe-hosted checkout URL`() {
        val b = booking()
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())

        mockkStatic(Session::class)
        val fakeSession = mockk<Session> { every { url } returns "https://checkout.stripe.com/session-xyz" }
        every { Session.create(any<SessionCreateParams>(), any<RequestOptions>()) } returns fakeSession

        val result = service.createBookingCheckoutSession(b.id!!)

        assertEquals("https://checkout.stripe.com/session-xyz", result.checkoutUrl)
    }

    // ---- handleCheckoutSessionCompleted ----

    @Test
    fun `handleCheckoutSessionCompleted marks a matching order paid and confirmed`() {
        val o = order(status = OrderStatus.PENDING)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        every { orderRepository.save(any()) } answers { firstArg() }
        val session = mockk<Session> {
            every { clientReferenceId } returns o.id.toString()
            every { paymentIntent } returns "pi_123"
        }

        service.handleCheckoutSessionCompleted(session)

        assertEquals(PaymentStatus.PAID, o.paymentStatus)
        assertEquals(OrderStatus.CONFIRMED, o.status)
        assertEquals("pi_123", o.stripePaymentIntentId)
        assertTrue(o.timeline.any { it.label == "Payment confirmed via Stripe" })
    }

    @Test
    fun `handleCheckoutSessionCompleted ignores a redelivered webhook for an already-paid order`() {
        val o = order(status = OrderStatus.CONFIRMED, paymentStatus = PaymentStatus.PAID)
        val timelineSizeBefore = o.timeline.size
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        val session = mockk<Session> {
            every { clientReferenceId } returns o.id.toString()
            every { paymentIntent } returns "pi_123"
        }

        service.handleCheckoutSessionCompleted(session)

        assertEquals(timelineSizeBefore, o.timeline.size)
        verify(exactly = 0) { orderRepository.save(any()) }
    }

    @Test
    fun `handleCheckoutSessionCompleted falls through to a matching booking`() {
        val b = booking(status = BookingStatus.PENDING)
        every { orderRepository.findById(b.id!!) } returns Optional.empty()
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        every { bookingRepository.save(any()) } answers { firstArg() }
        val session = mockk<Session> {
            every { clientReferenceId } returns b.id.toString()
            every { paymentIntent } returns "pi_456"
        }

        service.handleCheckoutSessionCompleted(session)

        assertEquals(PaymentStatus.PAID, b.paymentStatus)
        assertEquals(BookingStatus.CONFIRMED, b.status)
    }

    @Test
    fun `handleCheckoutSessionCompleted is a no-op when neither an order nor a booking matches`() {
        val id = UUID.randomUUID()
        every { orderRepository.findById(id) } returns Optional.empty()
        every { bookingRepository.findById(id) } returns Optional.empty()
        val session = mockk<Session> {
            every { clientReferenceId } returns id.toString()
        }

        service.handleCheckoutSessionCompleted(session)

        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    // ---- handleCheckoutSessionFailed ----

    @Test
    fun `handleCheckoutSessionFailed records a timeline entry without changing payment status`() {
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        every { orderRepository.save(any()) } answers { firstArg() }
        val session = mockk<Session> { every { clientReferenceId } returns o.id.toString() }

        service.handleCheckoutSessionFailed(session, "Stripe checkout session expired")

        assertEquals(PaymentStatus.UNPAID, o.paymentStatus)
        assertTrue(o.timeline.any { it.label == "Stripe checkout session expired" })
    }

    // ---- refundPayment ----

    @Test
    fun `refundPayment throws when the order has no Stripe payment intent`() {
        val o = order(stripePaymentIntentId = null)
        assertThrows(ConflictException::class.java) { service.refundPayment(o) }
    }

    @Test
    fun `refundPayment throws when the store has no connected Stripe account`() {
        val o = order(stripePaymentIntentId = "pi_123")
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings(accountId = null))
        assertThrows(ConflictException::class.java) { service.refundPayment(o) }
    }

    @Test
    fun `refundPayment issues a Stripe refund including the platform's application fee`() {
        val o = order(stripePaymentIntentId = "pi_123")
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())

        mockkStatic(Refund::class)
        every { Refund.create(any<RefundCreateParams>(), any<RequestOptions>()) } returns mockk<Refund>(relaxed = true)

        service.refundPayment(o)

        verify {
            Refund.create(
                match<RefundCreateParams> { it.paymentIntent == "pi_123" && it.refundApplicationFee },
                any<RequestOptions>(),
            )
        }
    }

    // ---- refundBookingPayment ----

    @Test
    fun `refundBookingPayment throws when the booking has no Stripe payment intent`() {
        val b = booking(stripePaymentIntentId = null)
        assertThrows(ConflictException::class.java) { service.refundBookingPayment(b) }
    }

    @Test
    fun `refundBookingPayment issues a Stripe refund`() {
        val b = booking(stripePaymentIntentId = "pi_789")
        every { storeSettingsRepository.findById(store.id!!) } returns Optional.of(storeSettings())

        mockkStatic(Refund::class)
        every { Refund.create(any<RefundCreateParams>(), any<RequestOptions>()) } returns mockk<Refund>(relaxed = true)

        service.refundBookingPayment(b)

        verify { Refund.create(match<RefundCreateParams> { it.paymentIntent == "pi_789" }, any<RequestOptions>()) }
    }
}
