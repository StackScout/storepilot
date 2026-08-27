package com.storepilot.backend.payout

import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookableService
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.ServiceStatus
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.notification.PayoutNotifier
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreCategory
import com.storepilot.backend.store.StoreRepository
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

class PayoutServiceTest {
    private val payoutRepository = mockk<PayoutRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val receiptStorageService = mockk<ReceiptStorageService>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val auditLogService = mockk<com.storepilot.backend.admin.AuditLogService>(relaxed = true)
    private val payoutNotifier = mockk<PayoutNotifier>(relaxed = true)

    private val service = PayoutService(
        payoutRepository,
        orderRepository,
        bookingRepository,
        storeRepository,
        receiptStorageService,
        fileStorageService,
        currentActor,
        auditLogService,
        payoutNotifier,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
    private val storeId: UUID = UUID.randomUUID()
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
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { payoutRepository.save(any()) } answers {
            (firstArg() as Payout).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
            }
        }
    }

    private fun payableOrder(
        id: UUID = UUID.randomUUID(),
        status: OrderStatus = OrderStatus.DELIVERED,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
        paymentMethod: PaymentMethod = PaymentMethod.PAYHERE,
        subtotal: Int = 1000,
        platformFee: Int = 50,
    ) = Order(
        orderNumber = "AU-20260101-1234",
        store = store,
        subtotal = subtotal,
        shippingFee = 0,
        platformFee = platformFee,
        total = subtotal,
        status = status,
        paymentMethod = paymentMethod,
        paymentStatus = paymentStatus,
        shipping = ShippingDetails(fullName = "Jane Doe", phone = "0400000000"),
        buyerEmail = "buyer@example.com",
        fulfillmentTimeHours = 48,
        deliveryTimeHours = 120,
    ).apply { this.id = id; createdAt = Instant.now() }

    private fun payableBooking(
        id: UUID = UUID.randomUUID(),
        status: BookingStatus = BookingStatus.COMPLETED,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
        paymentMethod: PaymentMethod = PaymentMethod.PAYHERE,
        servicePrice: Int = 500,
        platformFee: Int = 25,
    ): Booking {
        val bookableService = BookableService(
            store = store,
            name = "A service",
            slug = "a-service",
            description = "description",
            category = StoreCategory.HANDICRAFTS,
            price = servicePrice,
            durationMinutes = 60,
            status = ServiceStatus.ACTIVE,
        ).apply { this.id = UUID.randomUUID() }
        return Booking(
            bookingNumber = "AU-20260101-5678",
            store = store,
            service = bookableService,
            serviceName = "A service",
            servicePrice = servicePrice,
            serviceDurationMinutes = 60,
            scheduledStart = Instant.now(),
            scheduledEnd = Instant.now().plusSeconds(3600),
            platformFee = platformFee,
            total = servicePrice,
            status = status,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            buyerName = "John Smith",
            buyerPhone = "0400000000",
            buyerEmail = "buyer@example.com",
        ).apply { this.id = id; createdAt = Instant.now() }
    }

    private fun emptyPayoutHistory() {
        every { payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
    }

    // ---- listByStore / getEligibleOrders / getEligibleBookings ----

    @Test
    fun `listByStore rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        assertThrows(ForbiddenException::class.java) { service.listByStore(storeId) }
    }

    @Test
    fun `getEligibleOrders excludes orders that aren't delivered, paid PayHere`() {
        emptyPayoutHistory()
        val delivered = payableOrder()
        val notDelivered = payableOrder(status = OrderStatus.SHIPPED)
        val notPaid = payableOrder(paymentStatus = PaymentStatus.UNPAID)
        val notPayHere = payableOrder(paymentMethod = PaymentMethod.COD)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(delivered, notDelivered, notPaid, notPayHere)

        val result = service.getEligibleOrders(storeId)

        assertEquals(1, result.size)
        assertEquals(delivered.id, result[0].id)
    }

    @Test
    fun `getEligibleOrders excludes orders already included in a previous payout`() {
        val order = payableOrder()
        val existingPayout = Payout(store = store, subtotal = 1000, platformFee = 50, net = 950).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
            sourceRefs.add(PayoutSourceRef(payout = this, orderId = order.id, orderNumber = order.orderNumber, subtotal = 1000, platformFee = 50, net = 950))
        }
        every { payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(existingPayout)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(order)

        val result = service.getEligibleOrders(storeId)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getEligibleBookings excludes bookings that aren't completed, paid PayHere`() {
        emptyPayoutHistory()
        val completed = payableBooking()
        val notCompleted = payableBooking(status = BookingStatus.CONFIRMED)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(completed, notCompleted)

        val result = service.getEligibleBookings(storeId)

        assertEquals(1, result.size)
    }

    @Test
    fun `adminGetEligibleOrders doesn't require store ownership`() {
        emptyPayoutHistory()
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(payableOrder())

        val result = service.adminGetEligibleOrders(storeId)

        assertEquals(1, result.size)
        verify(exactly = 0) { currentActor.requireSeller() }
    }

    // ---- createBatch ----

    @Test
    fun `createBatch throws when there's nothing eligible`() {
        emptyPayoutHistory()
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()

        assertThrows(ConflictException::class.java) { service.createBatch(storeId) }
    }

    @Test
    fun `createBatch bundles eligible orders and bookings into one payout`() {
        emptyPayoutHistory()
        val order = payableOrder(subtotal = 1000, platformFee = 50)
        val booking = payableBooking(servicePrice = 500, platformFee = 25)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(order)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(booking)

        val result = service.createBatch(storeId)

        assertEquals(1500, result.subtotal)
        assertEquals(75, result.platformFee)
        assertEquals(1425, result.net)
        assertEquals(2, result.orders.size)
        assertEquals("scheduled", result.status)
    }

    // ---- markPaid ----

    @Test
    fun `markPaid throws for a missing payout`() {
        val id = UUID.randomUUID()
        every { payoutRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.markPaid(id, MarkPaidInput()) }
    }

    @Test
    fun `markPaid records the bank reference, audits, and notifies the seller`() {
        val payout = Payout(store = store, subtotal = 1000, platformFee = 50, net = 950).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
        }
        every { payoutRepository.findById(payout.id!!) } returns Optional.of(payout)

        val result = service.markPaid(payout.id!!, MarkPaidInput(bankReference = "REF123"))

        assertEquals("paid", result.status)
        assertEquals("REF123", result.bankReference)
        verify { auditLogService.record(com.storepilot.backend.admin.AuditAction.PAYOUT_MARKED_PAID, "payout", payout.id.toString(), any()) }
        verify { payoutNotifier.payoutMarkedPaid(payout) }
    }

    // ---- adminList ----

    @Test
    fun `adminList returns every payout, most recent first`() {
        val older = Payout(store = store, subtotal = 100, platformFee = 5, net = 95).apply { id = UUID.randomUUID(); createdAt = Instant.now().minusSeconds(60) }
        val newer = Payout(store = store, subtotal = 200, platformFee = 10, net = 190).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { payoutRepository.findAll() } returns listOf(older, newer)

        val result = service.adminList()

        assertEquals(newer.id, result.first().id)
    }
}
