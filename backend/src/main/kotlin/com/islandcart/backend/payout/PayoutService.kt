package com.islandcart.backend.payout

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.security.CurrentActor
import com.islandcart.backend.common.storage.FileStorageService
import com.islandcart.backend.order.Order
import com.islandcart.backend.order.OrderRepository
import com.islandcart.backend.order.OrderResponse
import com.islandcart.backend.order.OrderStatus
import com.islandcart.backend.order.PaymentStatus
import com.islandcart.backend.order.ReceiptStorageService
import com.islandcart.backend.order.toResponse
import com.islandcart.backend.store.StoreRepository
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
) {
    fun listByStore(storeId: UUID): List<PayoutResponse> {
        requireSellerOwnsStore(storeId)
        return payoutRepository.findByStoreIdOrderByCreatedAtDesc(storeId).map { it.toResponse() }
    }

    /**
     * Orders that are delivered + paid but not part of any payout (scheduled
     * or already paid) yet — money the platform is still holding on the
     * seller's behalf. Mirrors payouts.service.ts#getEligibleOrdersForPayout.
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
            .filter { it.status == OrderStatus.DELIVERED && it.paymentStatus == PaymentStatus.PAID && it.id !in alreadyIncluded }
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
        return payoutRepository.save(payout).toResponse()
    }

    fun adminList(): List<PayoutResponse> =
        payoutRepository.findAll().sortedByDescending { it.createdAt }.map { it.toResponse() }
}
