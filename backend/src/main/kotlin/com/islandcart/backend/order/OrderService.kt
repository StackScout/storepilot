package com.islandcart.backend.order

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.PageResponse
import com.islandcart.backend.common.PlatformConfigService
import com.islandcart.backend.common.ShippingDetails
import com.islandcart.backend.common.security.CurrentActor
import com.islandcart.backend.common.storage.FileStorageService
import com.islandcart.backend.common.storage.FileUploadPolicies
import com.islandcart.backend.common.toPageResponse
import com.islandcart.backend.common.wireValueOf
import com.islandcart.backend.notification.OrderNotifier
import com.islandcart.backend.product.ProductService
import com.islandcart.backend.store.StoreRepository
import com.islandcart.backend.store.StoreSettingsRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.UUID
import kotlin.random.Random

private val ORDER_NUMBER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendValue(ChronoField.YEAR, 4)
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .toFormatter()

private val STATUS_LABELS = mapOf(
    OrderStatus.PENDING to "Order placed",
    OrderStatus.CONFIRMED to "Order confirmed by seller",
    OrderStatus.SHIPPED to "Handed over to courier",
    OrderStatus.DELIVERED to "Delivered",
    OrderStatus.CANCELLED to "Cancelled",
)

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val productService: ProductService,
    private val receiptStorageService: ReceiptStorageService,
    private val fileStorageService: FileStorageService,
    private val orderNotifier: OrderNotifier,
    private val currentActor: CurrentActor,
    private val platformConfigService: PlatformConfigService,
) {
    /** GET /api/stores/{storeId}/orders — paginated: a long-running store can accumulate thousands of orders. */
    fun listByStore(storeId: UUID, status: String?, page: Int, size: Int): PageResponse<OrderResponse> {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")

        val statusEnum = status?.let { wireValueOf<OrderStatus>(it) }
        val pageable = PageRequest.of(page, size)
        val orders = if (statusEnum != null) {
            orderRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, statusEnum, pageable)
        } else {
            orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable)
        }
        return orders.toPageResponse { it.toResponse(receiptStorageService, fileStorageService) }
    }

    /**
     * GET /api/me/orders — buyerId always comes from CurrentActor, never a
     * path/query param (that was the by-buyerId IDOR gap this replaced).
     * Explicitly @Transactional (not the class default readOnly = true):
     * requireBuyer() may JIT-provision a new row on a caller's first
     * request, and that write fails under Postgres if nested inside a
     * read-only transaction (see CurrentActor.buyerOrNull's doc comment).
     */
    @Transactional
    fun listByCurrentBuyer(): List<OrderResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        return orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).map { it.toResponse(receiptStorageService, fileStorageService) }
    }

    fun getById(id: UUID): OrderResponse =
        orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }.toResponse(receiptStorageService, fileStorageService)

    /** GET /api/orders/lookup — order number (exact, case-insensitive) + last 9 digits of phone. */
    fun findByNumberAndPhone(orderNumber: String, phone: String): OrderResponse? {
        val normalizedInput = phone.replace(Regex("\\s+"), "")
        val suffix = normalizedInput.takeLast(9)
        val order = orderRepository.findByOrderNumberIgnoreCase(orderNumber.trim()) ?: return null
        val storedPhone = order.shipping.phone?.replace(Regex("\\s+"), "") ?: return null
        return if (storedPhone.endsWith(suffix)) order.toResponse(receiptStorageService, fileStorageService) else null
    }

    /** POST /api/orders — checkout. */
    @Transactional
    fun createOrder(input: CheckoutInput): OrderResponse {
        val resolvedItems = input.items.map { line ->
            val product = productService.findEntity(line.productId)
                ?: throw NotFoundException("Product ${line.productId} not found")
            Triple(product, line.quantity, product.store)
        }

        val store = resolvedItems.first().third
        val subtotal = resolvedItems.sumOf { (product, quantity, _) -> product.price * quantity }
        val platformConfig = platformConfigService.current()

        val feePercent = store.id
            ?.let { storeSettingsRepository.findById(it).orElse(null) }
            ?.transactionFeePercent
            ?: platformConfig.platformFeePercent
        val platformFee = (BigDecimal(subtotal) * feePercent)
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toInt()

        val now = Instant.now()
        val paymentMethod = wireValueOf<PaymentMethod>(input.paymentMethod)
        // Both start unpaid: COD flips to paid on delivery (see updateStatus),
        // PayHere flips to paid asynchronously via the notify webhook once the
        // buyer actually completes payment in the popup.
        val paymentStatus = PaymentStatus.UNPAID
        // Guest checkout stays unauthenticated (order ID is the credential
        // for later lookup) — this is null for a guest, never a
        // client-supplied field (see CheckoutInput's doc comment).
        val buyer = currentActor.buyerOrNull()

        val order = Order(
            orderNumber = generateOrderNumber(now, platformConfig.countryCode),
            store = store,
            subtotal = subtotal,
            shippingFee = platformConfig.flatShippingFee,
            platformFee = platformFee,
            total = subtotal + platformConfig.flatShippingFee,
            status = OrderStatus.PENDING,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            shipping = ShippingDetails(
                fullName = input.shipping.fullName,
                phone = input.shipping.phone,
                addressLine1 = input.shipping.addressLine1,
                city = input.shipping.city,
                state = input.shipping.state,
                postalCode = input.shipping.postalCode,
            ),
            buyerEmail = input.email,
            buyer = buyer,
        )
        resolvedItems.forEach { (product, quantity, _) ->
            order.items.add(
                OrderItem(
                    order = order,
                    productId = requireNotNull(product.id),
                    productName = product.name,
                    productImageUrl = product.images.firstOrNull()?.url ?: "",
                    unitPrice = product.price,
                    quantity = quantity,
                ),
            )
        }
        order.timeline.add(
            OrderTimelineEntry(order = order, status = OrderStatus.PENDING, label = STATUS_LABELS.getValue(OrderStatus.PENDING), timestamp = now),
        )

        val saved = orderRepository.save(order)
        productService.decrementStock(resolvedItems.map { (product, quantity, _) -> requireNotNull(product.id) to quantity })

        orderNotifier.orderConfirmed(saved)

        return saved.toResponse(receiptStorageService, fileStorageService)
    }

    /**
     * PATCH /api/orders/{id}/status. [courierReceipt] is only meaningful
     * when transitioning to "shipped" — an optional proof-of-handover
     * upload, attached to the shipped-notification email in addition to
     * being stored for later viewing on the order page.
     */
    @Transactional
    fun updateStatus(id: UUID, input: OrderStatusUpdateInput, courierReceipt: MultipartFile?): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
        requireSellerOwnsOrder(order)
        val status = wireValueOf<OrderStatus>(input.status)

        order.status = status
        if (status == OrderStatus.DELIVERED && order.paymentMethod == PaymentMethod.COD) {
            order.paymentStatus = PaymentStatus.PAID
        }
        if (status == OrderStatus.CANCELLED && order.paymentStatus == PaymentStatus.PAID) {
            order.paymentStatus = PaymentStatus.REFUNDED
        }

        if (status == OrderStatus.SHIPPED) {
            val trackingNumber = input.trackingNumber?.trim()
            val courierServiceName = input.courierServiceName?.trim()
            require(!trackingNumber.isNullOrBlank()) { "Tracking number is required to mark an order as shipped" }
            require(!courierServiceName.isNullOrBlank()) { "Courier service name is required to mark an order as shipped" }
            order.trackingNumber = trackingNumber
            order.courierServiceName = courierServiceName
            if (courierReceipt != null && !courierReceipt.isEmpty) {
                order.courierReceiptUrl = fileStorageService.store(
                    "courier-receipts",
                    courierReceipt,
                    FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
                    FileUploadPolicies.DOCUMENT_MAX_BYTES,
                )
            }
        }

        order.timeline.add(
            OrderTimelineEntry(
                order = order,
                status = status,
                label = STATUS_LABELS.getValue(status),
                timestamp = Instant.now(),
                note = input.note,
            ),
        )
        val saved = orderRepository.save(order)
        if (status == OrderStatus.SHIPPED) {
            orderNotifier.orderShipped(saved, courierReceipt)
        }
        return saved.toResponse(receiptStorageService, fileStorageService)
    }

    /** POST /api/orders/{id}/receipt — buyer uploads proof of a bank transfer. */
    @Transactional
    fun uploadReceipt(id: UUID, file: MultipartFile): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
        if (order.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Order $id is not a bank transfer payment")
        }
        if (order.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Order $id is already ${order.paymentStatus.wireValue}")
        }

        order.receiptUrl = receiptStorageService.store(file)
        order.timeline.add(
            OrderTimelineEntry(
                order = order,
                status = order.status,
                label = "Payment receipt uploaded",
                timestamp = Instant.now(),
                note = "Awaiting seller verification",
            ),
        )
        val saved = orderRepository.save(order)
        orderNotifier.receiptUploaded(saved)
        return saved.toResponse(receiptStorageService, fileStorageService)
    }

    /** POST /api/orders/{id}/verify-bank-transfer — seller accepts or rejects the uploaded receipt. */
    @Transactional
    fun verifyBankTransfer(id: UUID, input: VerifyBankTransferInput): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
        requireSellerOwnsOrder(order)
        if (order.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Order $id is not a bank transfer payment")
        }
        if (order.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Order $id is already ${order.paymentStatus.wireValue}")
        }

        if (input.approved) {
            order.paymentStatus = PaymentStatus.PAID
            if (order.status == OrderStatus.PENDING) order.status = OrderStatus.CONFIRMED
            order.timeline.add(
                OrderTimelineEntry(
                    order = order,
                    status = order.status,
                    label = "Payment confirmed by seller",
                    timestamp = Instant.now(),
                    note = input.note,
                ),
            )
        } else {
            // Stays pending/unpaid. Clearing receiptUrl (rather than leaving the
            // rejected one in place) puts the order back into the same "no
            // receipt on file" state as before any upload — which is what
            // re-enables both the buyer-cancel guard in
            // cancelBankTransferOrder and the upload form on the frontend, and
            // makes the order eligible for reminder emails again.
            order.receiptUrl = null
            order.timeline.add(
                OrderTimelineEntry(
                    order = order,
                    status = order.status,
                    label = "Payment receipt rejected",
                    timestamp = Instant.now(),
                    note = input.note,
                ),
            )
        }
        val saved = orderRepository.save(order)
        orderNotifier.bankTransferVerified(saved, input.approved, input.note)
        return saved.toResponse(receiptStorageService, fileStorageService)
    }

    /**
     * POST /api/orders/{id}/cancel — buyer-initiated cancel, reachable
     * unauthenticated (same "order ID is proof enough" model as GET/receipt
     * upload). Deliberately narrow: only a bank-transfer order still pending
     * with no receipt *currently on file* can be self-cancelled this way —
     * once a receipt is uploaded the seller is expected to act on it, not
     * have it pulled out from under them by the buyer. A rejected receipt
     * doesn't count as "on file" (verifyBankTransfer clears it), so cancel
     * is available again after a rejection. COD/PayHere orders have no
     * buyer-initiated cancel path.
     */
    @Transactional
    fun cancelBankTransferOrder(id: UUID): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
        if (order.paymentMethod != PaymentMethod.BANK_TRANSFER) {
            throw ConflictException("Order $id is not a bank transfer payment")
        }
        if (order.paymentStatus != PaymentStatus.UNPAID || order.status != OrderStatus.PENDING) {
            throw ConflictException("Order $id can no longer be cancelled")
        }
        if (order.receiptUrl != null) {
            throw ConflictException("A receipt has already been uploaded for order $id — contact the seller instead")
        }

        val hadRejectedReceipt = order.timeline.any { it.label == "Payment receipt rejected" }
        order.status = OrderStatus.CANCELLED
        order.timeline.add(
            OrderTimelineEntry(
                order = order,
                status = OrderStatus.CANCELLED,
                label = "Cancelled by buyer",
                timestamp = Instant.now(),
                note = if (hadRejectedReceipt) {
                    "Cancelled after the payment receipt was rejected"
                } else {
                    "Cancelled before a payment receipt was uploaded"
                },
            ),
        )
        return orderRepository.save(order).toResponse(receiptStorageService, fileStorageService)
    }

    private fun generateOrderNumber(now: Instant, countryCode: String): String {
        val datePart = ORDER_NUMBER_DATE_FORMAT.format(now.atZone(java.time.ZoneOffset.UTC))
        val randomPart = Random.nextInt(1000, 10000)
        return "$countryCode-$datePart-$randomPart"
    }

    private fun requireSellerOwnsOrder(order: Order) {
        val seller = currentActor.requireSeller()
        if (order.store.seller.id != seller.id) throw ForbiddenException("You don't own order ${order.id}")
    }
}
