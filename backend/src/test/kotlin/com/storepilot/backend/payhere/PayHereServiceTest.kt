package com.storepilot.backend.payhere

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
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreVerificationStatus
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
import java.util.Locale
import java.util.Optional
import java.util.UUID

class PayHereServiceTest {
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val platformConfigService = mockk<PlatformConfigService>()

    private val properties = PayHereProperties(
        merchantId = "merchant-1",
        merchantSecret = "top-secret",
        sandbox = true,
        notifyUrl = "http://localhost:8080/api/payments/payhere/notify",
        returnUrlBase = "http://localhost:3000/orders",
        bookingReturnUrlBase = "http://localhost:3000/bookings",
    )

    private val service = PayHereService(orderRepository, bookingRepository, properties, platformConfigService)

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
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }

        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot",
            tagline = "tagline",
            countryName = "Sri Lanka",
            countryCode = "LK",
            currencyCode = "LKR",
            currencySymbol = "Rs",
            currencyLocale = "en-LK",
            platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 500,
            proMonthlyPriceCents = 2900,
            defaultCodEnabled = true,
            defaultOnlinePaymentEnabled = false,
            defaultBankTransferEnabled = true,
            supportEmail = "hello@storepilot.au",
            companyLocation = "Colombo",
            timezone = "Asia/Colombo",
            returnWindowDays = 14,
        )
        every { orderRepository.save(any()) } answers { firstArg() }
        every { bookingRepository.save(any()) } answers { firstArg() }
    }

    private fun md5Upper(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.uppercase(Locale.ROOT)
    }

    private fun validSignature(orderId: String, amount: String, currency: String, statusCode: String): String =
        md5Upper(properties.merchantId + orderId + amount + currency + statusCode + md5Upper(properties.merchantSecret))

    private fun order(
        id: UUID = UUID.randomUUID(),
        total: Int = 10000,
        paymentMethod: PaymentMethod = PaymentMethod.PAYHERE,
        paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
        fullName: String? = "Jane Doe",
        status: OrderStatus = OrderStatus.PENDING,
    ) = Order(
        orderNumber = "LK-20260101-1234",
        store = store,
        subtotal = total,
        shippingFee = 0,
        platformFee = 0,
        total = total,
        status = status,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        shipping = ShippingDetails(fullName = fullName, phone = "0770000000", addressLine1 = "1 Main St", city = "Colombo"),
        buyerEmail = "buyer@example.com",
        fulfillmentTimeHours = 48,
        deliveryTimeHours = 120,
    ).apply { this.id = id }

    private fun booking(
        id: UUID = UUID.randomUUID(),
        total: Int = 5000,
        paymentMethod: PaymentMethod = PaymentMethod.PAYHERE,
        paymentStatus: PaymentStatus = PaymentStatus.UNPAID,
        buyerName: String = "John Smith",
        status: BookingStatus = BookingStatus.PENDING,
    ): Booking {
        val bookableService = BookableService(
            store = store,
            name = "A service",
            slug = "a-service",
            description = "description",
            category = "handicrafts",
            price = total,
            durationMinutes = 60,
            status = ServiceStatus.ACTIVE,
        ).apply { this.id = UUID.randomUUID() }
        return Booking(
            bookingNumber = "LK-20260101-5678",
            store = store,
            service = bookableService,
            serviceName = "A service",
            servicePrice = total,
            serviceDurationMinutes = 60,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(3600),
            platformFee = 0,
            total = total,
            status = status,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            buyerName = buyerName,
            buyerPhone = "0770000000",
            buyerEmail = "buyer@example.com",
        ).apply { this.id = id }
    }

    // ---- buildCheckoutPayload ----

    @Test
    fun `buildCheckoutPayload throws for a missing order`() {
        val id = UUID.randomUUID()
        every { orderRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.buildCheckoutPayload(id) }
    }

    @Test
    fun `buildCheckoutPayload rejects an order that isn't a PayHere payment`() {
        val o = order(paymentMethod = PaymentMethod.COD)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        assertThrows(ConflictException::class.java) { service.buildCheckoutPayload(o.id!!) }
    }

    @Test
    fun `buildCheckoutPayload rejects an order that's already paid`() {
        val o = order(paymentStatus = PaymentStatus.PAID)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        assertThrows(ConflictException::class.java) { service.buildCheckoutPayload(o.id!!) }
    }

    @Test
    fun `buildCheckoutPayload formats the amount as a decimal string and computes a matching hash`() {
        val o = order(total = 123456)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        val result = service.buildCheckoutPayload(o.id!!)

        assertEquals("1234.56", result.amount)
        val expectedHash = md5Upper(properties.merchantId + o.id.toString() + "1234.56" + "LKR" + md5Upper(properties.merchantSecret))
        assertEquals(expectedHash, result.hash)
    }

    @Test
    fun `buildCheckoutPayload splits the recipient's name into first and last`() {
        val o = order(fullName = "Jane Middle Doe")
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        val result = service.buildCheckoutPayload(o.id!!)

        assertEquals("Jane", result.firstName)
        assertEquals("Middle Doe", result.lastName)
    }

    @Test
    fun `buildCheckoutPayload falls back to Customer when there's no name at all`() {
        val o = order(fullName = null)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        val result = service.buildCheckoutPayload(o.id!!)

        assertEquals("Customer", result.firstName)
        assertEquals("Customer", result.lastName)
    }

    @Test
    fun `buildCheckoutPayload uses a single name as both first and last`() {
        val o = order(fullName = "Cher")
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        val result = service.buildCheckoutPayload(o.id!!)

        assertEquals("Cher", result.firstName)
        assertEquals("Cher", result.lastName)
    }

    @Test
    fun `buildCheckoutPayload points at the sandbox gateway when sandbox is enabled`() {
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        val result = service.buildCheckoutPayload(o.id!!)
        assertEquals("https://sandbox.payhere.lk/pay/checkout", result.actionUrl)
    }

    @Test
    fun `buildCheckoutPayload points at the live gateway when sandbox is disabled`() {
        val prodService = PayHereService(orderRepository, bookingRepository, properties.copy(sandbox = false), platformConfigService)
        val o = order()
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)
        val result = prodService.buildCheckoutPayload(o.id!!)
        assertEquals("https://www.payhere.lk/pay/checkout", result.actionUrl)
    }

    // ---- buildBookingCheckoutPayload ----

    @Test
    fun `buildBookingCheckoutPayload throws for a missing booking`() {
        val id = UUID.randomUUID()
        every { bookingRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.buildBookingCheckoutPayload(id) }
    }

    @Test
    fun `buildBookingCheckoutPayload rejects a booking that isn't a PayHere payment`() {
        val b = booking(paymentMethod = PaymentMethod.BANK_TRANSFER)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        assertThrows(ConflictException::class.java) { service.buildBookingCheckoutPayload(b.id!!) }
    }

    @Test
    fun `buildBookingCheckoutPayload rejects a booking that's already paid`() {
        val b = booking(paymentStatus = PaymentStatus.PAID)
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        assertThrows(ConflictException::class.java) { service.buildBookingCheckoutPayload(b.id!!) }
    }

    @Test
    fun `buildBookingCheckoutPayload leaves address fields blank`() {
        val b = booking()
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)
        val result = service.buildBookingCheckoutPayload(b.id!!)
        assertEquals("", result.address)
        assertEquals("", result.city)
    }

    // ---- verifyAndApplyNotification ----

    private fun notifyParams(orderId: String, amount: String, currency: String, statusCode: String, validSig: Boolean = true) = mapOf(
        "merchant_id" to properties.merchantId,
        "order_id" to orderId,
        "payhere_amount" to amount,
        "payhere_currency" to currency,
        "status_code" to statusCode,
        "md5sig" to if (validSig) validSignature(orderId, amount, currency, statusCode) else "BOGUS",
    )

    @Test
    fun `verifyAndApplyNotification ignores a payload missing required params`() {
        service.verifyAndApplyNotification(mapOf("merchant_id" to "x"))
        verify(exactly = 0) { orderRepository.findById(any()) }
    }

    @Test
    fun `verifyAndApplyNotification ignores a forged signature`() {
        val o = order()
        service.verifyAndApplyNotification(notifyParams(o.id.toString(), "100.00", "LKR", "2", validSig = false))
        verify(exactly = 0) { orderRepository.findById(any()) }
    }

    @Test
    fun `verifyAndApplyNotification ignores an unresolvable order or booking id`() {
        val id = UUID.randomUUID()
        every { orderRepository.findById(id) } returns Optional.empty()
        every { bookingRepository.findById(id) } returns Optional.empty()

        service.verifyAndApplyNotification(notifyParams(id.toString(), "100.00", "LKR", "2"))

        verify(exactly = 0) { orderRepository.save(any()) }
        verify(exactly = 0) { bookingRepository.save(any()) }
    }

    @Test
    fun `verifyAndApplyNotification marks a matching order paid and confirmed on success`() {
        val o = order(status = OrderStatus.PENDING)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        service.verifyAndApplyNotification(notifyParams(o.id.toString(), "100.00", "LKR", "2"))

        assertEquals(PaymentStatus.PAID, o.paymentStatus)
        assertEquals(OrderStatus.CONFIRMED, o.status)
        assertTrue(o.timeline.any { it.label == "Payment confirmed via PayHere" })
        verify { orderRepository.save(o) }
    }

    @Test
    fun `verifyAndApplyNotification records a cancelled payment without changing order status`() {
        val o = order(status = OrderStatus.PENDING)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        service.verifyAndApplyNotification(notifyParams(o.id.toString(), "100.00", "LKR", "-1"))

        assertEquals(PaymentStatus.UNPAID, o.paymentStatus)
        assertTrue(o.timeline.any { it.label == "PayHere payment cancelled" })
    }

    @Test
    fun `verifyAndApplyNotification records a failed payment without changing order status`() {
        val o = order(status = OrderStatus.PENDING)
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        service.verifyAndApplyNotification(notifyParams(o.id.toString(), "100.00", "LKR", "-2"))

        assertTrue(o.timeline.any { it.label == "PayHere payment failed" })
    }

    @Test
    fun `verifyAndApplyNotification falls through to the booking when no order matches the id`() {
        val b = booking(status = BookingStatus.PENDING)
        every { orderRepository.findById(b.id!!) } returns Optional.empty()
        every { bookingRepository.findById(b.id!!) } returns Optional.of(b)

        service.verifyAndApplyNotification(notifyParams(b.id.toString(), "50.00", "LKR", "2"))

        assertEquals(PaymentStatus.PAID, b.paymentStatus)
        assertEquals(BookingStatus.CONFIRMED, b.status)
        verify { bookingRepository.save(b) }
    }

    @Test
    fun `verifyAndApplyNotification only logs a pending status without applying any change`() {
        val o = order(status = OrderStatus.PENDING)
        val timelineSizeBefore = o.timeline.size
        every { orderRepository.findById(o.id!!) } returns Optional.of(o)

        service.verifyAndApplyNotification(notifyParams(o.id.toString(), "100.00", "LKR", "0"))

        assertEquals(timelineSizeBefore, o.timeline.size)
        assertEquals(PaymentStatus.UNPAID, o.paymentStatus)
        verify { orderRepository.save(o) }
    }
}
