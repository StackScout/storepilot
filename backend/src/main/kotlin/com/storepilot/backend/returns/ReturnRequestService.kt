package com.storepilot.backend.returns

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.notification.OrderNotifier
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.OrderTimelineEntry
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.stripe.StripeService
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/**
 * The post-delivery "buyer wants their money back" path — separate from
 * OrderService's pre-delivery cancellation, since the closed
 * ALLOWED_STATUS_TRANSITIONS state machine there doesn't allow any
 * transition out of DELIVERED. Own service, not folded into OrderService
 * (already 500+ lines), following StoreVerificationChangeRequestService's
 * precedent for "a decision on top of an existing entity."
 */
@Service
@Transactional(readOnly = true)
class ReturnRequestService(
    private val returnRequestRepository: ReturnRequestRepository,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val payoutRepository: PayoutRepository,
    private val feeCollectionRepository: FeeCollectionRepository,
    private val platformConfigService: PlatformConfigService,
    private val stripeService: StripeService,
    private val orderNotifier: OrderNotifier,
    private val currentActor: CurrentActor,
    private val auditLogService: AuditLogService,
    private val storeAccessService: StoreAccessService,
) {
    /**
     * POST /api/orders/{orderId}/returns — unauthenticated, same "order ID
     * is proof enough" model as receipt upload/cancel. Eligible only once
     * DELIVERED + PAID, within the configurable return window of the
     * order's *earliest* DELIVERED timeline entry — not the latest, since
     * DELIVERED -> DELIVERED is a legal self-loop in OrderService (a seller
     * can resubmit shipping details without the status changing), so
     * taking the latest would let the window be silently extended or
     * shrunk.
     */
    @Transactional
    fun create(orderId: UUID, input: ReturnRequestCreateInput): ReturnRequestResponse {
        val order = orderRepository.findById(orderId).orElseThrow { NotFoundException("Order $orderId not found") }
        if (order.status != OrderStatus.DELIVERED || order.paymentStatus != PaymentStatus.PAID) {
            throw ConflictException("Order $orderId isn't eligible for a return")
        }
        // Blocks on anything but REJECTED — REQUESTED/APPROVED/REFUND_PENDING
        // all mean a return is already in flight, and REFUNDED blocking too
        // is what stops an already-refunded order from being returned again
        // indefinitely. Only a rejected return allows resubmission.
        if (returnRequestRepository.existsByOrder_IdAndStatusNot(orderId, ReturnRequestStatus.REJECTED)) {
            throw ConflictException("A return is already in progress for order $orderId")
        }
        val deliveredAt = order.timeline
            .filter { it.status == OrderStatus.DELIVERED }
            .minByOrNull { it.timestamp }
            ?.timestamp
            ?: order.createdAt
            ?: throw ConflictException("Order $orderId has no delivery record")
        val windowDays = platformConfigService.current().returnWindowDays.toLong()
        if (deliveredAt.plus(windowDays, ChronoUnit.DAYS).isBefore(Instant.now())) {
            throw ConflictException("The return window for order $orderId has closed")
        }

        val request = returnRequestRepository.save(
            ReturnRequest(
                order = order,
                reasonCategory = wireValueOf<ReturnReasonCategory>(input.reasonCategory),
                reasonNote = input.reasonNote,
            ),
        )
        order.timeline.add(
            OrderTimelineEntry(order = order, status = order.status, label = "Return requested", timestamp = Instant.now(), note = input.reasonNote),
        )
        orderRepository.save(order)
        orderNotifier.returnRequested(order, request)
        return request.toResponse()
    }

    /** GET /api/orders/{orderId}/returns — same "order ID is proof enough" model; used by both the buyer and seller order-detail pages. */
    fun listForOrder(orderId: UUID): List<ReturnRequestResponse> =
        returnRequestRepository.findByOrder_IdOrderByCreatedAtDesc(orderId).map { it.toResponse() }

    /** GET /api/stores/{storeId}/returns — seller's own store. */
    fun listForStore(storeId: UUID, page: Int, size: Int): PageResponse<ReturnRequestResponse> {
        requireSellerOwnsStore(storeId)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return returnRequestRepository.findByOrder_Store_IdOrderByCreatedAtDesc(storeId, pageable).toPageResponse { it.toResponse() }
    }

    /**
     * POST /api/orders/{orderId}/returns/{returnId}/decision — seller
     * approve/reject. On approve: Stripe refunds synchronously in this same
     * call (reusing StripeService.refundPayment as-is, guarded by
     * paymentStatus == PAID immediately before the call since that method
     * has no internal idempotency check); every other payment method has no
     * live refund API, so it moves to REFUND_PENDING for a human to
     * confirm later (see markRefundedBySeller/adminMarkRefunded).
     */
    @Transactional
    fun decide(orderId: UUID, returnId: UUID, input: ReturnRequestDecisionInput): ReturnRequestResponse {
        val request = requireReturnOnOrder(orderId, returnId)
        if (request.status != ReturnRequestStatus.REQUESTED) {
            throw ConflictException("Return request $returnId has already been decided")
        }
        val order = request.order
        requireSellerOwnsOrder(order)

        request.decidedAt = Instant.now()
        request.sellerDecisionNote = input.note

        if (!input.approved) {
            request.status = ReturnRequestStatus.REJECTED
            val saved = returnRequestRepository.save(request)
            order.timeline.add(
                OrderTimelineEntry(order = order, status = order.status, label = "Return rejected", timestamp = Instant.now(), note = input.note),
            )
            orderRepository.save(order)
            orderNotifier.returnDecided(order, false, input.note)
            return saved.toResponse()
        }

        request.status = ReturnRequestStatus.APPROVED
        if (order.paymentMethod != PaymentMethod.STRIPE) {
            // Skipped for Stripe — direct charges never enter either ledger,
            // see PaymentMethod.STRIPE's doc comment.
            request.settlementReconciliationNote = settlementReconciliationNote(order)
        }

        val timelineLabel: String
        if (order.paymentMethod == PaymentMethod.STRIPE) {
            if (order.paymentStatus != PaymentStatus.PAID) {
                throw ConflictException("Order ${order.id} is already ${order.paymentStatus.wireValue}")
            }
            stripeService.refundPayment(order)
            order.paymentStatus = PaymentStatus.REFUNDED
            request.status = ReturnRequestStatus.REFUNDED
            request.refundedAt = Instant.now()
            timelineLabel = "Return approved — refunded"
            auditLogService.recordAsSeller(
                currentActor.requireSeller(),
                AuditAction.RETURN_REFUND_MARKED_COMPLETE,
                "return_request",
                returnId.toString(),
                "Refunded order ${order.orderNumber} via Stripe on return approval",
            )
        } else {
            request.status = ReturnRequestStatus.REFUND_PENDING
            timelineLabel = "Return approved"
        }

        val saved = returnRequestRepository.save(request)
        order.timeline.add(
            OrderTimelineEntry(order = order, status = order.status, label = timelineLabel, timestamp = Instant.now(), note = input.note),
        )
        orderRepository.save(order)
        orderNotifier.returnDecided(order, true, input.note)
        return saved.toResponse()
    }

    /**
     * POST /api/orders/{orderId}/returns/{returnId}/mark-refunded — seller
     * self-attests, for COD/bank-transfer only. This money moves directly
     * from the seller's own account back to the buyer; the platform is
     * never a party to it — exactly mirroring OrderService.verifyBankTransfer's
     * existing precedent of the seller attesting to money only the seller
     * can see (as opposed to Payout/FeeCollection's admin-confirmed model,
     * which is for money the platform's own merchant account is a party to
     * — see adminMarkRefunded).
     */
    @Transactional
    fun markRefundedBySeller(orderId: UUID, returnId: UUID, input: ReturnRequestMarkRefundedInput): ReturnRequestResponse {
        val request = requireReturnOnOrder(orderId, returnId)
        val order = request.order
        requireSellerOwnsOrder(order)
        if (order.paymentMethod != PaymentMethod.COD && order.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Order ${order.id}'s refund must be confirmed by an admin, not the seller")
        }
        if (request.status != ReturnRequestStatus.REFUND_PENDING) {
            throw ConflictException("Return request $returnId isn't awaiting a refund")
        }
        val saved = completeRefund(request, order, input.refundReference)
        auditLogService.recordAsSeller(
            currentActor.requireSeller(),
            AuditAction.RETURN_REFUND_MARKED_COMPLETE,
            "return_request",
            returnId.toString(),
            "Seller marked order ${order.orderNumber}'s return as refunded",
        )
        return saved
    }

    /**
     * PATCH /api/admin/returns/{returnId} — admin confirms a PayHere
     * refund, mirroring PayoutService.markPaid exactly: PayHere is "the one
     * payment method where the platform's own merchant account actually
     * receives the charge" (PayoutService.getEligibleOrders's doc comment),
     * so only the platform can truthfully attest the money moved back out
     * of it. Gated by SecurityConfig's blanket admin-role rule on every
     * /api/admin path, no extra check needed here.
     */
    @Transactional
    fun adminMarkRefunded(returnId: UUID, input: ReturnRequestMarkRefundedInput): ReturnRequestResponse {
        val request = returnRequestRepository.findById(returnId).orElseThrow { NotFoundException("Return request $returnId not found") }
        val order = request.order
        if (order.paymentMethod != PaymentMethod.PAYHERE) {
            throw ConflictException("Order ${order.id}'s refund must be confirmed by the seller, not an admin")
        }
        if (request.status != ReturnRequestStatus.REFUND_PENDING) {
            throw ConflictException("Return request $returnId isn't awaiting a refund")
        }
        val saved = completeRefund(request, order, input.refundReference)
        auditLogService.record(
            AuditAction.RETURN_REFUND_MARKED_COMPLETE,
            "return_request",
            returnId.toString(),
            "Admin marked order ${order.orderNumber}'s PayHere return as refunded",
        )
        return saved
    }

    /** GET /api/admin/returns — gated by SecurityConfig's blanket admin-role rule on every /api/admin path. */
    fun adminList(status: String?, page: Int, size: Int): PageResponse<ReturnRequestResponse> {
        val statusEnum = status?.let { wireValueOf<ReturnRequestStatus>(it) }
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val requests = if (statusEnum != null) {
            returnRequestRepository.findByStatusOrderByCreatedAtDesc(statusEnum, pageable)
        } else {
            returnRequestRepository.findAllByOrderByCreatedAtDesc(pageable)
        }
        return requests.toPageResponse { it.toResponse() }
    }

    private fun completeRefund(request: ReturnRequest, order: Order, refundReference: String?): ReturnRequestResponse {
        request.status = ReturnRequestStatus.REFUNDED
        request.refundReference = refundReference
        request.refundedAt = Instant.now()
        order.paymentStatus = PaymentStatus.REFUNDED
        val saved = returnRequestRepository.save(request)
        order.timeline.add(
            OrderTimelineEntry(
                order = order,
                status = order.status,
                label = "Refund completed",
                timestamp = Instant.now(),
                note = refundReference?.let { "Ref: $it" },
            ),
        )
        orderRepository.save(order)
        orderNotifier.returnRefunded(order, refundReference)
        return saved.toResponse()
    }

    /**
     * Detects whether [order] was already included in a Payout/
     * FeeCollection batch — reuses the exact flatMap-over-sourceRefs
     * pattern PayoutService/FeeCollectionService already use for their own
     * eligibility checks (see PayoutService.eligibleOrderEntities), rather
     * than adding a new repository query. Computed once, at
     * seller-approval time, and frozen onto the request — same
     * "snapshot, don't re-derive" principle as PayoutSourceRef itself.
     * There's no automatic clawback for an already-settled batch in this
     * codebase (no line-item-removal mechanism exists for any batch,
     * scheduled or paid) — this is a visible flag for manual admin
     * reconciliation, not a fix.
     */
    private fun settlementReconciliationNote(order: Order): String? {
        val storeId = requireNotNull(order.store.id)
        val orderId = requireNotNull(order.id)
        when (order.paymentMethod) {
            PaymentMethod.PAYHERE -> {
                val payout = payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
                    .firstOrNull { payout -> payout.sourceRefs.any { it.orderId == orderId } }
                    ?: return null
                return if (payout.status == PayoutStatus.PAID) {
                    "Already paid out to the seller in a completed payout — reconcile this refund manually (e.g. deduct from the seller's next payout)."
                } else {
                    "Included in a scheduled (not yet paid) payout — no money has moved yet; adjust the batch before marking it paid."
                }
            }
            PaymentMethod.COD, PaymentMethod.BANK_TRANSFER -> {
                val feeCollection = feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
                    .firstOrNull { feeCollection -> feeCollection.sourceRefs.any { it.orderId == orderId } }
                    ?: return null
                return if (feeCollection.status == FeeCollectionStatus.COLLECTED) {
                    "The platform's fee for this order was already collected — reconcile this refund's fee portion manually."
                } else {
                    "Included in a pending (not yet collected) fee collection — no money has moved yet; adjust the batch before marking it collected."
                }
            }
            PaymentMethod.STRIPE -> return null
        }
    }

    private fun requireReturnOnOrder(orderId: UUID, returnId: UUID): ReturnRequest {
        val request = returnRequestRepository.findById(returnId).orElseThrow { NotFoundException("Return request $returnId not found") }
        if (requireNotNull(request.order.id) != orderId) {
            throw NotFoundException("Return request $returnId not found for order $orderId")
        }
        return request
    }

    private fun requireSellerOwnsOrder(order: Order) {
        storeAccessService.requireOperationalAccess(order.store)
    }

    private fun requireSellerOwnsStore(storeId: UUID) {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        storeAccessService.requireOperationalAccess(store)
    }
}
