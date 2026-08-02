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

/** Mirrors PayoutService exactly, just for the reverse direction — see FeeCollection's doc comment. */
@Service
@Transactional(readOnly = true)
class FeeCollectionService(
    private val feeCollectionRepository: FeeCollectionRepository,
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val receiptStorageService: ReceiptStorageService,
    private val fileStorageService: FileStorageService,
    private val currentActor: CurrentActor,
    private val auditLogService: AuditLogService,
) {
    fun listByStore(storeId: UUID): List<FeeCollectionResponse> {
        requireSellerOwnsStore(storeId)
        return feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId).map { it.toResponse() }
    }

    /**
     * COD/bank-transfer orders that are delivered + paid but not part of any
     * fee collection (pending or already collected) yet — money the seller
     * is holding that the platform hasn't been paid its cut on. Mirrors
     * PayoutService#eligibleOrders, opposite direction.
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
        val alreadyIncluded = feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { feeCollection -> feeCollection.orders.map { it.orderId } }
            .toSet()
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == OrderStatus.DELIVERED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    (it.paymentMethod == PaymentMethod.COD || it.paymentMethod == PaymentMethod.BANK_TRANSFER) &&
                    it.id !in alreadyIncluded
            }
    }

    /** POST /api/admin/stores/{storeId}/fee-collections — bundle all eligible orders into one batch owed by this seller. */
    @Transactional
    fun createBatch(storeId: UUID): FeeCollectionResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val eligible = eligibleOrderEntities(storeId)
        if (eligible.isEmpty()) throw ConflictException("No eligible orders to collect fees for from this store")

        val feeCollection = FeeCollection(
            store = store,
            subtotal = eligible.sumOf { it.subtotal },
            platformFee = eligible.sumOf { it.platformFee },
            status = FeeCollectionStatus.PENDING,
        )
        eligible.forEach { order ->
            feeCollection.orders.add(
                FeeCollectionOrderRef(
                    feeCollection = feeCollection,
                    orderId = requireNotNull(order.id),
                    orderNumber = order.orderNumber,
                    subtotal = order.subtotal,
                    platformFee = order.platformFee,
                ),
            )
        }
        return feeCollectionRepository.save(feeCollection).toResponse()
    }

    /** PATCH /api/admin/fee-collections/{id} — admin confirms the seller actually paid the platform its fee. */
    @Transactional
    fun markCollected(feeCollectionId: UUID, input: MarkCollectedInput): FeeCollectionResponse {
        val feeCollection = feeCollectionRepository.findById(feeCollectionId)
            .orElseThrow { NotFoundException("Fee collection $feeCollectionId not found") }
        feeCollection.status = FeeCollectionStatus.COLLECTED
        feeCollection.collectedAt = Instant.now()
        feeCollection.reference = input.reference
        val saved = feeCollectionRepository.save(feeCollection)
        val referenceSuffix = input.reference?.let { " (ref: $it)" } ?: ""
        auditLogService.record(
            AuditAction.FEE_COLLECTION_MARKED_COLLECTED,
            "fee_collection",
            feeCollectionId.toString(),
            "Marked fee collection for \"${feeCollection.store.name}\" (${feeCollection.platformFee}) as collected$referenceSuffix",
        )
        return saved.toResponse()
    }

    fun adminList(): List<FeeCollectionResponse> =
        feeCollectionRepository.findAll().sortedByDescending { it.createdAt }.map { it.toResponse() }
}
