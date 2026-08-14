package com.storepilot.backend.payout

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingResponse
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.toResponse
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
    private val bookingRepository: BookingRepository,
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

    /** Same eligibility rule as getEligibleOrders, for bookings — COMPLETED (the appointment-lifecycle analog of DELIVERED) + PAID + PAYHERE. */
    fun getEligibleBookings(storeId: UUID): List<BookingResponse> {
        requireSellerOwnsStore(storeId)
        return eligibleBookingEntities(storeId).map { it.toResponse(receiptStorageService) }
    }

    private fun requireSellerOwnsStore(storeId: UUID) {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
    }

    private fun eligibleOrderEntities(storeId: UUID): List<Order> {
        val alreadyIncluded = payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { payout -> payout.sourceRefs.mapNotNull { it.orderId } }
            .toSet()
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == OrderStatus.DELIVERED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    it.paymentMethod == PaymentMethod.PAYHERE &&
                    it.id !in alreadyIncluded
            }
    }

    private fun eligibleBookingEntities(storeId: UUID): List<Booking> {
        val alreadyIncluded = payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { payout -> payout.sourceRefs.mapNotNull { it.bookingId } }
            .toSet()
        return bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == BookingStatus.COMPLETED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    it.paymentMethod == PaymentMethod.PAYHERE &&
                    it.id !in alreadyIncluded
            }
    }

    /**
     * POST /api/admin/stores/{storeId}/payouts — bundles every eligible
     * order AND booking for this store into one scheduled payout, so a
     * store selling both products and bookable services gets a single
     * reconciliation run rather than two disconnected ledgers — see
     * PayoutSourceRef's doc comment.
     */
    @Transactional
    fun createBatch(storeId: UUID): PayoutResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val eligibleOrders = eligibleOrderEntities(storeId)
        val eligibleBookings = eligibleBookingEntities(storeId)
        if (eligibleOrders.isEmpty() && eligibleBookings.isEmpty()) throw ConflictException("No eligible orders or bookings to pay out for this store")

        val payout = Payout(
            store = store,
            subtotal = eligibleOrders.sumOf { it.subtotal } + eligibleBookings.sumOf { it.servicePrice },
            platformFee = eligibleOrders.sumOf { it.platformFee } + eligibleBookings.sumOf { it.platformFee },
            net = eligibleOrders.sumOf { it.subtotal - it.platformFee } + eligibleBookings.sumOf { it.servicePrice - it.platformFee },
            status = PayoutStatus.SCHEDULED,
        )
        eligibleOrders.forEach { order ->
            payout.sourceRefs.add(
                PayoutSourceRef(
                    payout = payout,
                    orderId = requireNotNull(order.id),
                    orderNumber = order.orderNumber,
                    subtotal = order.subtotal,
                    platformFee = order.platformFee,
                    net = order.subtotal - order.platformFee,
                ),
            )
        }
        eligibleBookings.forEach { booking ->
            payout.sourceRefs.add(
                PayoutSourceRef(
                    payout = payout,
                    bookingId = requireNotNull(booking.id),
                    bookingNumber = booking.bookingNumber,
                    subtotal = booking.servicePrice,
                    platformFee = booking.platformFee,
                    net = booking.servicePrice - booking.platformFee,
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
