package com.storepilot.backend.payout

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderResponse
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.order.toResponse
import com.storepilot.backend.store.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PayoutService(
    private val payoutRepository: PayoutRepository,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val receiptStorageService: ReceiptStorageService,
    private val fileStorageService: FileStorageService,
    private val currentActor: CurrentActor,
    private val auditLogService: AuditLogService,
) {
    fun listByStore(storeId: UUID): List<PayoutResponse> {
        requireSellerOwnsStore(storeId)
        return payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId).map { it.toResponse() }
    }

    /**
     * PayHere orders that are delivered + paid but not part of any payout
     * (scheduled or already paid) yet — money the platform is still holding
     * on the seller's behalf. **Only PayHere** — it's the one payment method
     * where the platform's own merchant account actually receives the
     * charge; COD/bank-transfer pay the seller directly (see
     * FeeCollectionService, the reverse-direction ledger for those), and
     * Stripe Connect direct charges settle automatically at charge time
     * (see PaymentMethod.STRIPE's doc comment) — neither ever belongs here.
     */
    fun getEligibleOrders(storeId: UUID): List<OrderResponse> {
        requireSellerOwnsStore(storeId)
        return eligibleOrderEntities(storeId).map { it.toResponse(receiptStorageService, fileStorageService) }
    }

    private fun requireSellerOwnsStore(storeId: UUID) {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
    }

    private fun eligibleOrderEntities(storeId: UUID): List<Order> {
        val alreadyIncluded = payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { payout -> payout.orders.map { it.orderId } }
            .toSet()
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == OrderStatus.DELIVERED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    it.paymentMethod == PaymentMethod.PAYHERE &&
                    it.id !in alreadyIncluded
            }
    }

    /** POST /api/admin/stores/{storeId}/payouts — bundle all eligible orders into one scheduled payout. */
    @Transactional
    fun createBatch(storeId: UUID): PayoutResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val eligible = eligibleOrderEntities(storeId)
        if (eligible.isEmpty()) throw ConflictException("No eligible orders to pay out for this store")

        val payout = Payout(
            store = store,
            subtotal = eligible.sumOf { it.subtotal },
            platformFee = eligible.sumOf { it.platformFee },
            net = eligible.sumOf { it.subtotal - it.platformFee },
            status = PayoutStatus.SCHEDULED,
        )
        eligible.forEach { order ->
            payout.orders.add(
                PayoutOrderRef(
                    payout = payout,
                    orderId = requireNotNull(order.id),
                    orderNumber = order.orderNumber,
                    subtotal = order.subtotal,
                    platformFee = order.platformFee,
                    net = order.subtotal - order.platformFee,
                ),
            )
        }
        return payoutRepository.save(payout).toResponse()
    }

    /** PATCH /api/admin/payouts/{payoutId} — admin confirms the bank transfer actually went out. */
    @Transactional
    fun markPaid(payoutId: UUID, input: MarkPaidInput): PayoutResponse {
        val payout = payoutRepository.findById(payoutId).orElseThrow { NotFoundException("Payout $payoutId not found") }
        payout.status = PayoutStatus.PAID
        payout.paidAt = Instant.now()
        payout.bankReference = input.bankReference
        val saved = payoutRepository.save(payout)
        val referenceSuffix = input.bankReference?.let { " (ref: $it)" } ?: ""
        auditLogService.record(
            AuditAction.PAYOUT_MARKED_PAID,
            "payout",
            payoutId.toString(),
            "Marked payout for \"${payout.store.name}\" (net ${payout.net}) as paid$referenceSuffix",
        )
        return saved.toResponse()
    }

    fun adminList(): List<PayoutResponse> =
        payoutRepository.findAll().sortedByDescending { it.createdAt }.map { it.toResponse() }
}
