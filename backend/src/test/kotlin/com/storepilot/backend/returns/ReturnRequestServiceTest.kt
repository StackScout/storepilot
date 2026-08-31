package com.storepilot.backend.returns

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.OrderNotifier
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.OrderTimelineEntry
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.payout.FeeCollection
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionSourceRef
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.Payout
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutSourceRef
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreStaffMemberRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.storepilot.backend.stripe.StripeService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class ReturnRequestServiceTest {
    private val returnRequestRepository = mockk<ReturnRequestRepository>()
    private val orderRepository = mockk<OrderRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val payoutRepository = mockk<PayoutRepository>()
    private val feeCollectionRepository = mockk<FeeCollectionRepository>()
    private val platformConfigService = mockk<PlatformConfigService>()
    private val stripeService = mockk<StripeService>(relaxed = true)
    private val orderNotifier = mockk<OrderNotifier>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val auditLogService = mockk<AuditLogService>(relaxed = true)
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>(relaxed = true)
    private val storeAccessService = StoreAccessService(currentActor, storeStaffMemberRepository)

    private val service = ReturnRequestService(
        returnRequestRepository,
        orderRepository,
        storeRepository,
        payoutRepository,
        feeCollectionRepository,
        platformConfigService,
        stripeService,
        orderNotifier,
        currentActor,
        auditLogService,
        storeAccessService,
    )

    private val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller")
    private val storeId: UUID = UUID.randomUUID()
    private lateinit var store: Store
    private lateinit var order: Order

    private fun newOrder(paymentMethod: PaymentMethod, deliveredAt: Instant = Instant.now().minus(1, ChronoUnit.DAYS)): Order =
        Order(
            orderNumber = "AU-20260101-0001",
            store = store,
            subtotal = 1000,
            shippingFee = 100,
            platformFee = 20,
            total = 1100,
            status = OrderStatus.DELIVERED,
            paymentMethod = paymentMethod,
            paymentStatus = PaymentStatus.PAID,
            shipping = ShippingDetails(fullName = "Buyer", phone = "+61400000000"),
            buyerEmail = "buyer@example.com",
            fulfillmentTimeHours = 48,
            deliveryTimeHours = 120,
        ).apply {
            id = UUID.randomUUID()
            createdAt = deliveredAt
            timeline.add(OrderTimelineEntry(order = this, status = OrderStatus.DELIVERED, label = "Delivered", timestamp = deliveredAt))
        }

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = seller,
            slug = "test-store",
            name = "Test Store",
            tagline = "tagline",
            description = "description",
            category = "handicrafts",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
            verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = storeId }
        order = newOrder(PaymentMethod.COD)
        every { currentActor.requireSeller() } returns seller
        every { orderRepository.findById(any()) } returns Optional.of(order)
        every { orderRepository.save(any()) } answers { firstArg() }
        every { platformConfigService.current() } returns mockk<PlatformSettings> { every { returnWindowDays } returns 30 }
        every { returnRequestRepository.save(any()) } answers {
            (firstArg() as ReturnRequest).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
            }
        }
        every { payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
        every { feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns emptyList()
    }

    // --- create ---

    @Test
    fun `create rejects an order that isn't delivered and paid`() {
        order.status = OrderStatus.SHIPPED
        every { returnRequestRepository.existsByOrder_IdAndStatusNot(any(), any()) } returns false

        assertThrows(ConflictException::class.java) {
            service.create(requireNotNull(order.id), ReturnRequestCreateInput(reasonCategory = "changed-mind"))
        }
    }

    @Test
    fun `create rejects a second request while one is already in progress`() {
        every { returnRequestRepository.existsByOrder_IdAndStatusNot(requireNotNull(order.id), ReturnRequestStatus.REJECTED) } returns true

        assertThrows(ConflictException::class.java) {
            service.create(requireNotNull(order.id), ReturnRequestCreateInput(reasonCategory = "changed-mind"))
        }
    }

    @Test
    fun `create allows a new request after a prior one was rejected`() {
        every { returnRequestRepository.existsByOrder_IdAndStatusNot(requireNotNull(order.id), ReturnRequestStatus.REJECTED) } returns false

        val response = service.create(requireNotNull(order.id), ReturnRequestCreateInput(reasonCategory = "defective", reasonNote = "Arrived broken"))

        assertEquals("defective", response.reasonCategory)
        assertEquals(ReturnRequestStatus.REQUESTED.wireValue, response.status)
        verify { orderNotifier.returnRequested(order, any()) }
    }

    @Test
    fun `create rejects a request outside the return window`() {
        order = newOrder(PaymentMethod.COD, deliveredAt = Instant.now().minus(45, ChronoUnit.DAYS))
        every { orderRepository.findById(any()) } returns Optional.of(order)
        every { returnRequestRepository.existsByOrder_IdAndStatusNot(any(), any()) } returns false

        assertThrows(ConflictException::class.java) {
            service.create(requireNotNull(order.id), ReturnRequestCreateInput(reasonCategory = "changed-mind"))
        }
    }

    @Test
    fun `create uses the earliest delivered timeline entry, not the latest`() {
        // A DELIVERED -> DELIVERED self-loop (see OrderService's transition
        // map) shouldn't silently extend the window by re-timestamping it.
        order.timeline.add(
            OrderTimelineEntry(order = order, status = OrderStatus.DELIVERED, label = "Delivered", timestamp = Instant.now()),
        )
        order.timeline.add(
            0,
            OrderTimelineEntry(order = order, status = OrderStatus.DELIVERED, label = "Delivered", timestamp = Instant.now().minus(45, ChronoUnit.DAYS)),
        )
        every { returnRequestRepository.existsByOrder_IdAndStatusNot(any(), any()) } returns false

        assertThrows(ConflictException::class.java) {
            service.create(requireNotNull(order.id), ReturnRequestCreateInput(reasonCategory = "changed-mind"))
        }
    }

    // --- decide ---

    @Test
    fun `decide reject sets REJECTED and never touches payment status`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.CHANGED_MIND).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = false, note = "Item was used"))

        assertEquals(ReturnRequestStatus.REJECTED.wireValue, response.status)
        assertEquals(PaymentStatus.PAID, order.paymentStatus)
        verify { orderNotifier.returnDecided(order, false, "Item was used") }
    }

    @Test
    fun `decide approve on Stripe refunds synchronously and lands on REFUNDED`() {
        order = newOrder(PaymentMethod.STRIPE).apply { stripePaymentIntentId = "pi_123" }
        every { orderRepository.findById(any()) } returns Optional.of(order)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.DEFECTIVE).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))

        assertEquals(ReturnRequestStatus.REFUNDED.wireValue, response.status)
        assertEquals(PaymentStatus.REFUNDED, order.paymentStatus)
        assertNull(response.settlementReconciliationNote, "Stripe orders never enter the Payout/FeeCollection ledgers")
        verify { stripeService.refundPayment(order) }
        verify { auditLogService.recordAsSeller(seller, AuditAction.RETURN_REFUND_MARKED_COMPLETE, "return_request", request.id.toString(), any()) }
    }

    @Test
    fun `decide approve on PayHere lands on REFUND_PENDING without calling Stripe`() {
        order = newOrder(PaymentMethod.PAYHERE)
        every { orderRepository.findById(any()) } returns Optional.of(order)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.WRONG_ITEM).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))

        assertEquals(ReturnRequestStatus.REFUND_PENDING.wireValue, response.status)
        assertEquals(PaymentStatus.PAID, order.paymentStatus, "no live PayHere refund API — money hasn't moved yet")
        verify(exactly = 0) { stripeService.refundPayment(any()) }
    }

    @Test
    fun `decide approve flags an order already paid out in a settled Payout`() {
        order = newOrder(PaymentMethod.PAYHERE)
        every { orderRepository.findById(any()) } returns Optional.of(order)
        val payout = Payout(store = store, subtotal = 1000, platformFee = 20, net = 980, status = PayoutStatus.PAID)
        payout.sourceRefs.add(PayoutSourceRef(payout = payout, orderId = order.id, orderNumber = order.orderNumber, subtotal = 1000, platformFee = 20, net = 980))
        every { payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(payout)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))

        assertEquals(true, response.settlementReconciliationNote?.contains("already paid out", ignoreCase = true))
    }

    @Test
    fun `decide rejects a non-owning seller`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.CHANGED_MIND).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)
        every { currentActor.requireSeller() } returns Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }

        assertThrows(ForbiddenException::class.java) {
            service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))
        }
    }

    @Test
    fun `decide rejects a request that was already decided`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.CHANGED_MIND, status = ReturnRequestStatus.APPROVED).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        assertThrows(ConflictException::class.java) {
            service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))
        }
    }

    @Test
    fun `decide 404s when the return doesn't belong to the given order`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.CHANGED_MIND).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        assertThrows(NotFoundException::class.java) {
            service.decide(UUID.randomUUID(), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))
        }
    }

    // --- markRefundedBySeller ---

    @Test
    fun `markRefundedBySeller completes a COD refund`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER, status = ReturnRequestStatus.REFUND_PENDING).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.markRefundedBySeller(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestMarkRefundedInput(refundReference = "cash-handoff"))

        assertEquals(ReturnRequestStatus.REFUNDED.wireValue, response.status)
        assertEquals(PaymentStatus.REFUNDED, order.paymentStatus)
        verify { auditLogService.recordAsSeller(seller, AuditAction.RETURN_REFUND_MARKED_COMPLETE, "return_request", request.id.toString(), any()) }
        verify { orderNotifier.returnRefunded(order, "cash-handoff") }
    }

    @Test
    fun `markRefundedBySeller rejects a PayHere order — only an admin can confirm that refund`() {
        order = newOrder(PaymentMethod.PAYHERE)
        every { orderRepository.findById(any()) } returns Optional.of(order)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER, status = ReturnRequestStatus.REFUND_PENDING).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        assertThrows(ConflictException::class.java) {
            service.markRefundedBySeller(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestMarkRefundedInput())
        }
    }

    @Test
    fun `markRefundedBySeller rejects a request that isn't awaiting a refund`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER, status = ReturnRequestStatus.REQUESTED).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        assertThrows(ConflictException::class.java) {
            service.markRefundedBySeller(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestMarkRefundedInput())
        }
    }

    // --- adminMarkRefunded ---

    @Test
    fun `adminMarkRefunded completes a PayHere refund`() {
        order = newOrder(PaymentMethod.PAYHERE)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER, status = ReturnRequestStatus.REFUND_PENDING).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.adminMarkRefunded(requireNotNull(request.id), ReturnRequestMarkRefundedInput(refundReference = "payhere-refund-88"))

        assertEquals(ReturnRequestStatus.REFUNDED.wireValue, response.status)
        assertEquals(PaymentStatus.REFUNDED, order.paymentStatus)
        verify { auditLogService.record(AuditAction.RETURN_REFUND_MARKED_COMPLETE, "return_request", request.id.toString(), any()) }
    }

    @Test
    fun `adminMarkRefunded rejects a COD order — the seller must self-attest that one`() {
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER, status = ReturnRequestStatus.REFUND_PENDING).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        assertThrows(ConflictException::class.java) {
            service.adminMarkRefunded(requireNotNull(request.id), ReturnRequestMarkRefundedInput())
        }
    }

    // --- reconciliation with fee collections (COD/bank-transfer side) ---

    @Test
    fun `decide approve flags an order already fee-collected for COD`() {
        val feeCollection = FeeCollection(store = store, subtotal = 1000, platformFee = 20, status = FeeCollectionStatus.COLLECTED)
        feeCollection.sourceRefs.add(FeeCollectionSourceRef(feeCollection = feeCollection, orderId = order.id, orderNumber = order.orderNumber, subtotal = 1000, platformFee = 20))
        every { feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(feeCollection)
        val request = ReturnRequest(order = order, reasonCategory = ReturnReasonCategory.OTHER).apply { id = UUID.randomUUID() }
        every { returnRequestRepository.findById(requireNotNull(request.id)) } returns Optional.of(request)

        val response = service.decide(requireNotNull(order.id), requireNotNull(request.id), ReturnRequestDecisionInput(approved = true))

        assertEquals(true, response.settlementReconciliationNote?.contains("already collected"))
    }
}
