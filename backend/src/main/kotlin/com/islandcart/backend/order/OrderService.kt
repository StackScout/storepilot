package com.islandcart.backend.order

import com.islandcart.backend.buyer.BuyerRepository
import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.FLAT_SHIPPING_FEE_LKR
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.PLATFORM_FEE_PERCENT
import com.islandcart.backend.common.ShippingDetails
import com.islandcart.backend.common.wireValueOf
import com.islandcart.backend.notification.OrderNotifier
import com.islandcart.backend.product.ProductService
import com.islandcart.backend.store.StoreSettingsRepository
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
    private val storeSettingsRepository: StoreSettingsRepository,
    private val buyerRepository: BuyerRepository,
    private val productService: ProductService,
    private val receiptStorageService: ReceiptStorageService,
    private val orderNotifier: OrderNotifier,
) {
    fun listByStore(storeId: UUID, status: String?): List<OrderResponse> {
        val statusEnum = status?.let { wireValueOf<OrderStatus>(it) }
        return orderRepository.findByStoreIdOrderByCreatedAtDesc(storeId)
            .filter { statusEnum == null || it.status == statusEnum }
            .map { it.toResponse(receiptStorageService) }
    }

    fun listByBuyer(buyerId: UUID): List<OrderResponse> =
        orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).map { it.toResponse(receiptStorageService) }

    fun getById(id: UUID): OrderResponse =
        orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }.toResponse(receiptStorageService)

    /** GET /api/orders/lookup — order number (exact, case-insensitive) + last 9 digits of phone. */
    fun findByNumberAndPhone(orderNumber: String, phone: String): OrderResponse? {
        val normalizedInput = phone.replace(Regex("\\s+"), "")
        val suffix = normalizedInput.takeLast(9)
        val order = orderRepository.findByOrderNumberIgnoreCase(orderNumber.trim()) ?: return null
        val storedPhone = order.shipping.phone?.replace(Regex("\\s+"), "") ?: return null
        return if (storedPhone.endsWith(suffix)) order.toResponse(receiptStorageService) else null
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
        val subtotalLkr = resolvedItems.sumOf { (product, quantity, _) -> product.priceLkr * quantity }

        val feePercent = store.id
            ?.let { storeSettingsRepository.findById(it).orElse(null) }
            ?.transactionFeePercent
            ?: PLATFORM_FEE_PERCENT
        val platformFeeLkr = (BigDecimal(subtotalLkr) * feePercent)
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toInt()

        val now = Instant.now()
        val paymentMethod = wireValueOf<PaymentMethod>(input.paymentMethod)
        // Both start unpaid: COD flips to paid on delivery (see updateStatus),
        // PayHere flips to paid asynchronously via the notify webhook once the
        // buyer actually completes payment in the popup.
        val paymentStatus = PaymentStatus.UNPAID
        val buyer = input.buyerId?.let { buyerRepository.findById(it).orElse(null) }

        val order = Order(
            orderNumber = generateOrderNumber(now),
            store = store,
            subtotalLkr = subtotalLkr,
            shippingFeeLkr = FLAT_SHIPPING_FEE_LKR,
            platformFeeLkr = platformFeeLkr,
            totalLkr = subtotalLkr + FLAT_SHIPPING_FEE_LKR,
            status = OrderStatus.PENDING,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            shipping = ShippingDetails(
                fullName = input.shipping.fullName,
                phone = input.shipping.phone,
                addressLine1 = input.shipping.addressLine1,
                city = input.shipping.city,
                district = input.shipping.district,
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
                    unitPriceLkr = product.priceLkr,
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

        return saved.toResponse(receiptStorageService)
    }

    /** PATCH /api/orders/{id}/status */
    @Transactional
    fun updateStatus(id: UUID, input: OrderStatusUpdateInput): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
        val status = wireValueOf<OrderStatus>(input.status)

        order.status = status
        if (status == OrderStatus.DELIVERED && order.paymentMethod == PaymentMethod.COD) {
            order.paymentStatus = PaymentStatus.PAID
        }
        if (status == OrderStatus.CANCELLED && order.paymentStatus == PaymentStatus.PAID) {
            order.paymentStatus = PaymentStatus.REFUNDED
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
        return orderRepository.save(order).toResponse(receiptStorageService)
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
        return saved.toResponse(receiptStorageService)
    }

    /** POST /api/orders/{id}/verify-bank-transfer — seller accepts or rejects the uploaded receipt. */
    @Transactional
    fun verifyBankTransfer(id: UUID, input: VerifyBankTransferInput): OrderResponse {
        val order = orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }
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
            // Stays pending/unpaid — the buyer can upload a corrected receipt and try again.
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
        return saved.toResponse(receiptStorageService)
    }

    /**
     * POST /api/orders/{id}/cancel — buyer-initiated cancel, reachable
     * unauthenticated (same "order ID is proof enough" model as GET/receipt
     * upload). Deliberately narrow: only a bank-transfer order still pending
     * with no receipt uploaded yet can be self-cancelled this way — once a
     * receipt is uploaded the seller is expected to act on it, not have it
     * pulled out from under them by the buyer. COD/PayHere orders have no
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

        order.status = OrderStatus.CANCELLED
        order.timeline.add(
            OrderTimelineEntry(
                order = order,
                status = OrderStatus.CANCELLED,
                label = "Cancelled by buyer",
                timestamp = Instant.now(),
                note = "Cancelled before a payment receipt was uploaded",
            ),
        )
        return orderRepository.save(order).toResponse(receiptStorageService)
    }

    private fun generateOrderNumber(now: Instant): String {
        val datePart = ORDER_NUMBER_DATE_FORMAT.format(now.atZone(java.time.ZoneOffset.UTC))
        val randomPart = Random.nextInt(1000, 10000)
        return "SL-$datePart-$randomPart"
    }
}
