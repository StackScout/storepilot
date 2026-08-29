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
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class FeeCollectionServiceTest {
    private val feeCollectionRepository = mockk<FeeCollectionRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val bookingRepository = mockk<BookingRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val receiptStorageService = mockk<ReceiptStorageService>(relaxed = true)
    private val fileStorageService = mockk<FileStorageService>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val auditLogService = mockk<com.storepilot.backend.admin.AuditLogService>(relaxed = true)

    private val service = FeeCollectionService(
        feeCollectionRepository,
        orderRepository,
        bookingRepository,
        storeRepository,
        receiptStorageService,
        fileStorageService,
        currentActor,
        auditLogService,
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
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        every { currentActor.requireSeller() } returns seller
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { feeCollectionRepository.save(any()) } answers {
            (firstArg() as FeeCollection).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
            }
        }
    }

    private fun owedOrder(
        id: UUID = UUID.randomUUID(),
        status: OrderStatus = OrderStatus.DELIVERED,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
        paymentMethod: PaymentMethod = PaymentMethod.COD,
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

    private fun owedBooking(
        id: UUID = UUID.randomUUID(),
        status: BookingStatus = BookingStatus.COMPLETED,
        paymentStatus: PaymentStatus = PaymentStatus.PAID,
        paymentMethod: PaymentMethod = PaymentMethod.BANK_TRANSFER,
        servicePrice: Int = 500,
        platformFee: Int = 25,
    ): Booking {
        val bookableService = BookableService(
            store = store,
            name = "A service",
            slug = "a-service",
            description = "description",
            category = "handicrafts",
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

    private fun emptyHistory() {
        every { feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
    }

    // ---- listByStore ----

    @Test
    fun `listByStore rejects a non-owning seller`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns otherSeller
        assertThrows(ForbiddenException::class.java) { service.listByStore(storeId, 0, 20) }
    }

    // ---- getEligibleOrders / getEligibleBookings ----

    @Test
    fun `getEligibleOrders accepts both COD and bank transfer, excludes PayHere and Stripe`() {
        emptyHistory()
        val cod = owedOrder(paymentMethod = PaymentMethod.COD)
        val bankTransfer = owedOrder(paymentMethod = PaymentMethod.BANK_TRANSFER)
        val payHere = owedOrder(paymentMethod = PaymentMethod.PAYHERE)
        val stripe = owedOrder(paymentMethod = PaymentMethod.STRIPE)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(cod, bankTransfer, payHere, stripe)

        val result = service.getEligibleOrders(storeId, 0, 20)

        assertEquals(2, result.content.size)
        assertTrue(result.content.map { it.id }.containsAll(listOf(cod.id, bankTransfer.id)))
    }

    @Test
    fun `getEligibleOrders excludes orders already in a previous fee collection`() {
        val order = owedOrder()
        val existing = FeeCollection(store = store, subtotal = 1000, platformFee = 50).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
            sourceRefs.add(FeeCollectionSourceRef(feeCollection = this, orderId = order.id, orderNumber = order.orderNumber, subtotal = 1000, platformFee = 50))
        }
        every { feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(existing)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(order)

        assertTrue(service.getEligibleOrders(storeId, 0, 20).content.isEmpty())
    }

    @Test
    fun `getEligibleBookings requires completed status`() {
        emptyHistory()
        val completed = owedBooking()
        val pending = owedBooking(status = BookingStatus.PENDING)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(completed, pending)

        val result = service.getEligibleBookings(storeId, 0, 20)

        assertEquals(1, result.content.size)
    }

    @Test
    fun `adminGetEligibleBookings doesn't require store ownership`() {
        emptyHistory()
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(owedBooking())

        val result = service.adminGetEligibleBookings(storeId, 0, 20)

        assertEquals(1, result.content.size)
        verify(exactly = 0) { currentActor.requireSeller() }
    }

    // ---- createBatch ----

    @Test
    fun `createBatch throws when nothing is owed`() {
        emptyHistory()
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()

        assertThrows(ConflictException::class.java) { service.createBatch(storeId) }
    }

    @Test
    fun `createBatch bundles owed orders and bookings into one collection`() {
        emptyHistory()
        val order = owedOrder(subtotal = 1000, platformFee = 50)
        val booking = owedBooking(servicePrice = 500, platformFee = 25)
        every { orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(order)
        every { bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(booking)

        val result = service.createBatch(storeId)

        assertEquals(1500, result.subtotal)
        assertEquals(75, result.platformFee)
        assertEquals("pending", result.status)
        assertEquals(2, result.orders.size)
    }

    // ---- markCollected ----

    @Test
    fun `markCollected throws for a missing fee collection`() {
        val id = UUID.randomUUID()
        every { feeCollectionRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.markCollected(id, MarkCollectedInput()) }
    }

    @Test
    fun `markCollected records the reference and audits the decision`() {
        val feeCollection = FeeCollection(store = store, subtotal = 1000, platformFee = 50).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
        }
        every { feeCollectionRepository.findById(feeCollection.id!!) } returns Optional.of(feeCollection)

        val result = service.markCollected(feeCollection.id!!, MarkCollectedInput(reference = "INV-001"))

        assertEquals("collected", result.status)
        assertEquals("INV-001", result.reference)
        verify {
            auditLogService.record(
                com.storepilot.backend.admin.AuditAction.FEE_COLLECTION_MARKED_COLLECTED,
                "fee_collection",
                feeCollection.id.toString(),
                any(),
            )
        }
    }

    // ---- adminList ----

    @Test
    fun `adminList returns every fee collection, most recent first`() {
        val older = FeeCollection(store = store, subtotal = 100, platformFee = 5).apply { id = UUID.randomUUID(); createdAt = Instant.now().minusSeconds(60) }
        val newer = FeeCollection(store = store, subtotal = 200, platformFee = 10).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { feeCollectionRepository.findAllByOrderByCreatedAtDesc(any()) } returns PageImpl(listOf(newer, older))

        val result = service.adminList(0, 20)

        assertEquals(newer.id, result.content.first().id)
    }
}
