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
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderResponse
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.order.ReceiptStorageService
import com.storepilot.backend.order.toResponse
import com.storepilot.backend.store.StoreRepository
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/** Mirrors PayoutService's own private helper of the same name — see its doc comment for why eligible-orders/eligible-bookings use in-memory "shallow" pagination rather than a Pageable-driven DB query. */
private fun <T : Any, R> List<T>.toPageResponseInMemory(requestedPage: Int, requestedSize: Int, mapper: (T) -> R): PageResponse<R> {
    val boundedPage = requestedPage.coerceAtLeast(0)
    val boundedSize = requestedSize.coerceIn(1, MAX_PAGE_SIZE)
    val totalElements = size.toLong()
    val pageContent: List<T> = drop(boundedPage * boundedSize).take(boundedSize)
    val pageImpl = PageImpl<T>(pageContent, PageRequest.of(boundedPage, boundedSize), totalElements)
    return PageResponse(
        content = pageImpl.content.map(mapper),
        page = pageImpl.number,
        size = pageImpl.size,
        totalElements = pageImpl.totalElements,
        totalPages = pageImpl.totalPages,
    )
}

/** Mirrors PayoutService exactly, just for the reverse direction — see FeeCollection's doc comment. */
@Service
@Transactional(readOnly = true)
class FeeCollectionService(
    private val feeCollectionRepository: FeeCollectionRepository,
    private val orderRepository: OrderRepository,
    private val bookingRepository: BookingRepository,
    private val storeRepository: StoreRepository,
    private val receiptStorageService: ReceiptStorageService,
    private val fileStorageService: FileStorageService,
    private val currentActor: CurrentActor,
    private val auditLogService: AuditLogService,
) {
    fun listByStore(storeId: UUID, page: Int, size: Int): PageResponse<FeeCollectionResponse> {
        requireSellerOwnsStore(storeId)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable).toPageResponse { it.toResponse() }
    }

    /**
     * COD/bank-transfer orders that are delivered + paid but not part of any
     * fee collection (pending or already collected) yet — money the seller
     * is holding that the platform hasn't been paid its cut on. Mirrors
     * PayoutService#eligibleOrders, opposite direction.
     */
    fun getEligibleOrders(storeId: UUID, page: Int, size: Int): PageResponse<OrderResponse> {
        requireSellerOwnsStore(storeId)
        return eligibleOrderEntities(storeId).toPageResponseInMemory(page, size) { it.toResponse(receiptStorageService, fileStorageService) }
    }

    /** Mirrors getEligibleOrders, opposite direction, for bookings paid via "Pay at venue"/bank-transfer. */
    fun getEligibleBookings(storeId: UUID, page: Int, size: Int): PageResponse<BookingResponse> {
        requireSellerOwnsStore(storeId)
        return eligibleBookingEntities(storeId).toPageResponseInMemory(page, size) { it.toResponse(receiptStorageService) }
    }

    /**
     * Admin-facing equivalent of getEligibleOrders — used by the accounting
     * dashboard's "which stores owe a fee collection" scan across every
     * active store, not just the caller's own. Deliberately skips
     * requireSellerOwnsStore, mirroring PayoutService.adminGetEligibleOrders
     * and the same split createBatch below already relies on. Reachable
     * only under the "/api/admin" prefix, already gated to hasRole("ADMIN")
     * by SecurityConfig as a whole.
     */
    fun adminGetEligibleOrders(storeId: UUID, page: Int, size: Int): PageResponse<OrderResponse> =
        eligibleOrderEntities(storeId).toPageResponseInMemory(page, size) { it.toResponse(receiptStorageService, fileStorageService) }

    /** Admin-facing equivalent of getEligibleBookings — see adminGetEligibleOrders's doc comment. */
    fun adminGetEligibleBookings(storeId: UUID, page: Int, size: Int): PageResponse<BookingResponse> =
        eligibleBookingEntities(storeId).toPageResponseInMemory(page, size) { it.toResponse(receiptStorageService) }

    private fun requireSellerOwnsStore(storeId: UUID) {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
    }

    private fun eligibleOrderEntities(storeId: UUID): List<Order> {
        val alreadyIncluded = feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { feeCollection -> feeCollection.sourceRefs.mapNotNull { it.orderId } }
            .toSet()
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == OrderStatus.DELIVERED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    (it.paymentMethod == PaymentMethod.COD || it.paymentMethod == PaymentMethod.BANK_TRANSFER) &&
                    it.id !in alreadyIncluded
            }
    }

    private fun eligibleBookingEntities(storeId: UUID): List<Booking> {
        val alreadyIncluded = feeCollectionRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .flatMap { feeCollection -> feeCollection.sourceRefs.mapNotNull { it.bookingId } }
            .toSet()
        return bookingRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter {
                it.status == BookingStatus.COMPLETED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    (it.paymentMethod == PaymentMethod.COD || it.paymentMethod == PaymentMethod.BANK_TRANSFER) &&
                    it.id !in alreadyIncluded
            }
    }

    /** POST /api/admin/stores/{storeId}/fee-collections — bundles every eligible order AND booking owed by this seller into one batch, same unified-reconciliation reasoning as PayoutService.createBatch. */
    @Transactional
    fun createBatch(storeId: UUID): FeeCollectionResponse {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val eligibleOrders = eligibleOrderEntities(storeId)
        val eligibleBookings = eligibleBookingEntities(storeId)
        if (eligibleOrders.isEmpty() && eligibleBookings.isEmpty()) throw ConflictException("No eligible orders or bookings to collect fees for from this store")

        val feeCollection = FeeCollection(
            store = store,
            subtotal = eligibleOrders.sumOf { it.subtotal } + eligibleBookings.sumOf { it.servicePrice },
            platformFee = eligibleOrders.sumOf { it.platformFee } + eligibleBookings.sumOf { it.platformFee },
            status = FeeCollectionStatus.PENDING,
        )
        eligibleOrders.forEach { order ->
            feeCollection.sourceRefs.add(
                FeeCollectionSourceRef(
                    feeCollection = feeCollection,
                    orderId = requireNotNull(order.id),
                    orderNumber = order.orderNumber,
                    subtotal = order.subtotal,
                    platformFee = order.platformFee,
                ),
            )
        }
        eligibleBookings.forEach { booking ->
            feeCollection.sourceRefs.add(
                FeeCollectionSourceRef(
                    feeCollection = feeCollection,
                    bookingId = requireNotNull(booking.id),
                    bookingNumber = booking.bookingNumber,
                    subtotal = booking.servicePrice,
                    platformFee = booking.platformFee,
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

    fun adminList(page: Int, size: Int): PageResponse<FeeCollectionResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return feeCollectionRepository.findAllByOrderByCreatedAtDesc(pageable).toPageResponse { it.toResponse() }
    }
}
