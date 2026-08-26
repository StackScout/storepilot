package com.storepilot.backend.order

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.GuestLookupOtpService
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.sse.SseHub
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.storage.FileUploadPolicies
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.common.wireValueOf
import com.storepilot.backend.coupon.CouponKind
import com.storepilot.backend.coupon.CouponService
import com.storepilot.backend.notification.OrderNotifier
import com.storepilot.backend.product.ProductService
import com.storepilot.backend.seller.SellerPlan
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.stripe.StripeService
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

/**
 * Server-side mirror of the frontend's OrderStatusSelect NEXT_STATUS_OPTIONS
 * map — the single source of truth now lives here (see updateStatus), not
 * just in the dashboard dropdown. Each non-terminal status maps to itself
 * plus its allowed forward moves (self-transition stays legal so a seller
 * can resubmit shipping details without the status itself changing);
 * delivered/cancelled are terminal — no transition out of either.
 */
private val ALLOWED_STATUS_TRANSITIONS: Map<OrderStatus, Set<OrderStatus>> = mapOf(
    OrderStatus.PENDING to setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
    OrderStatus.CONFIRMED to setOf(OrderStatus.CONFIRMED, OrderStatus.SHIPPED, OrderStatus.CANCELLED),
    OrderStatus.SHIPPED to setOf(OrderStatus.SHIPPED, OrderStatus.DELIVERED),
    OrderStatus.DELIVERED to setOf(OrderStatus.DELIVERED),
    OrderStatus.CANCELLED to setOf(OrderStatus.CANCELLED),
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
    private val stripeService: StripeService,
    private val guestLookupOtpService: GuestLookupOtpService,
    private val sseHub: SseHub,
    private val couponService: CouponService,
) {
    /** Fan-out to any subscribers on GET /api/orders/{id}/events — call after every write that changes what the buyer/seller sees on the order. */
    private fun publishOrderEvent(order: Order): OrderResponse {
        val response = order.toResponse(receiptStorageService, fileStorageService)
        sseHub.publish("order:${order.id}", "status", response)
        return response
    }

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

    /**
     * GET /api/stores/{storeId}/stripe-settlements — read-only reconciliation
     * view of paid Stripe orders for this store: what went through Stripe,
     * what Stripe auto-paid the seller, what the platform automatically
     * took. Never a ledger to release/collect from — Connect already moved
     * the money at charge time (see PaymentMethod.STRIPE's doc comment).
     */
    fun listStripeSettlementsByStore(storeId: UUID): List<OrderResponse> {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        return orderRepository.findByStoreIdAndPaymentMethodAndPaymentStatusOrderByCreatedAtDesc(storeId, PaymentMethod.STRIPE, PaymentStatus.PAID)
            .map { it.toResponse(receiptStorageService, fileStorageService) }
    }

    /** GET /api/admin/stripe-settlements — same view, platform-wide. */
    fun adminListStripeSettlements(): List<OrderResponse> =
        orderRepository.findByPaymentMethodAndPaymentStatusOrderByCreatedAtDesc(PaymentMethod.STRIPE, PaymentStatus.PAID)
            .map { it.toResponse(receiptStorageService, fileStorageService) }

    fun getById(id: UUID): OrderResponse =
        orderRepository.findById(id).orElseThrow { NotFoundException("Order $id not found") }.toResponse(receiptStorageService, fileStorageService)

    /** Order number (exact, case-insensitive) + last 9 digits of phone — the first factor of guest lookup, shared by both steps below. */
    private fun resolveByNumberAndPhone(orderNumber: String, phone: String): Order? {
        val normalizedInput = phone.replace(Regex("\\s+"), "")
        val suffix = normalizedInput.takeLast(9)
        val order = orderRepository.findByOrderNumberIgnoreCase(orderNumber.trim()) ?: return null
        val storedPhone = order.shipping.phone?.replace(Regex("\\s+"), "") ?: return null
        return if (storedPhone.endsWith(suffix)) order else null
    }

    /**
     * POST /api/orders/lookup/request-code — first step of guest lookup.
     * Order number + phone alone is guessable at scale (see
     * docs/roadmap.md's "Order lookup credential strength" gap); this
     * emails a one-time code to the order's own buyer email as a second
     * factor before verifyLookupCode below reveals anything. Silently a
     * no-op when the number/phone don't match — same "don't leak whether
     * it matched" principle as EmailVerificationService.resendVerificationCode,
     * so an attacker fishing for valid order numbers learns nothing from
     * the response either way.
     */
    @Transactional
    fun requestLookupCode(orderNumber: String, phone: String) {
        val order = resolveByNumberAndPhone(orderNumber, phone) ?: return
        guestLookupOtpService.requestCode(
            targetType = "order",
            targetId = requireNotNull(order.id),
            email = order.buyerEmail,
            recipientName = order.shipping.fullName ?: "there",
        )
    }

    /** POST /api/orders/lookup/verify — second step, completes the lookup. Throws NotFoundException (number/phone mismatch) or IllegalArgumentException (bad/expired code — see GuestLookupOtpService.verifyCode). */
    @Transactional
    fun verifyLookupCode(orderNumber: String, phone: String, code: String): OrderResponse {
        val order = resolveByNumberAndPhone(orderNumber, phone) ?: throw NotFoundException("Order not found")
        guestLookupOtpService.verifyCode("order", requireNotNull(order.id), code)
        return order.toResponse(receiptStorageService, fileStorageService)
    }

    /** POST /api/orders — checkout. */
    @Transactional
    fun createOrder(input: CheckoutInput): OrderResponse {
        val resolvedItems = input.items.map { line ->
            val product = productService.findEntity(line.productId)
                ?: throw NotFoundException("Product ${line.productId} not found")
            Triple(product, line.quantity, product.store)
        }
        // product.trackStock is already the AND of the store's and the
        // product's own stock-management opt-in (see ProductService's
        // effectiveTrackStock) — so a product with tracking off, or
        // belonging to a store with stock management disabled, is skipped
        // here entirely, same as decrementStock does after checkout.
        resolvedItems.forEach { (product, quantity, _) ->
            if (product.trackStock && quantity > product.stockQuantity) {
                throw ConflictException("${product.name} only has ${product.stockQuantity} left in stock")
            }
        }

        val store = resolvedItems.first().third
        val subtotal = resolvedItems.sumOf { (product, quantity, _) -> product.price * quantity }
        val platformConfig = platformConfigService.current()
        val storeSettings = store.id?.let { storeSettingsRepository.findById(it).orElse(null) }

        // Resolved (validated + computed, not yet recorded as used) before
        // the fee/total math below — a coupon discounts the subtotal that
        // both the platform fee and the buyer's total are derived from. Use
        // is only recorded after the order is actually persisted (below),
        // so a failed/aborted checkout never burns a use.
        val couponResolution = input.couponCode?.takeIf { it.isNotBlank() }
            ?.let { couponService.resolve(it, requireNotNull(store.id), CouponKind.ORDER, subtotal) }
        val discountAmount = couponResolution?.discountAmount ?: 0
        val discountedSubtotal = subtotal - discountAmount

        // fullName/phone matter regardless of delivery method (a courier or
        // the seller both need someone to hand the order to); the rest of
        // the address is meaningless for pickup, so it's only required for
        // shipping — same conditional-requiredness pattern as updateStatus's
        // SHIPPED-only tracking fields. See ShippingDetailsInput's doc
        // comment for why this isn't just a @Valid annotation instead.
        val deliveryMethod = wireValueOf<DeliveryMethod>(input.deliveryMethod)
        require(input.shipping.fullName.isNotBlank()) { "Enter the recipient's full name" }
        require(input.shipping.phone.isNotBlank()) { "Enter a valid phone number" }
        if (deliveryMethod == DeliveryMethod.SHIPPING) {
            require(!input.shipping.addressLine1.isNullOrBlank()) { "Enter the delivery address" }
            require(!input.shipping.city.isNullOrBlank()) { "Enter a city/town" }
            require(!input.shipping.state.isNullOrBlank()) { "Select a state/province" }
            require(!input.shipping.postalCode.isNullOrBlank()) { "Enter a postal code" }
        } else {
            if (storeSettings?.pickupEnabled != true) throw ConflictException("This store doesn't offer pickup")
        }
        val shippingFee = if (deliveryMethod == DeliveryMethod.PICKUP) 0 else platformConfig.flatShippingFee

        val feePercent = storeSettings?.transactionFeePercent ?: platformConfig.platformFeePercent
        val platformFee = (BigDecimal(discountedSubtotal) * feePercent)
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
            .toInt()

        val now = Instant.now()
        val paymentMethod = wireValueOf<PaymentMethod>(input.paymentMethod)
        // Defense in depth — StoreService.upsertSettings already refuses to
        // let a non-Pro seller turn these two on in the first place, but a
        // seller who downgrades after enabling them (or a stale client
        // submitting a payment method the settings toggle wouldn't offer)
        // must still be blocked here, not just hidden in the UI.
        if ((paymentMethod == PaymentMethod.COD || paymentMethod == PaymentMethod.BANK_TRANSFER) && store.seller.plan != SellerPlan.PRO) {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }
        // PayHere is Sri Lanka-specific, Stripe is Australia-specific — each
        // is temporarily disabled outside its home market. UI already hides
        // both accordingly; this is defense in depth for a stale client.
        if (paymentMethod == PaymentMethod.PAYHERE && platformConfig.countryCode != "LK") {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }
        if (paymentMethod == PaymentMethod.STRIPE && platformConfig.countryCode != "AU") {
            throw ConflictException("This store doesn't offer ${paymentMethod.wireValue} payments")
        }
        // Both start unpaid: COD flips to paid on delivery (see updateStatus),
        // PayHere flips to paid asynchronously via the notify webhook once the
        // buyer actually completes payment in the popup.
        val paymentStatus = PaymentStatus.UNPAID
        // Guest checkout stays unauthenticated (order ID is the credential
        // for later lookup) — this is null for a guest, never a
        // client-supplied field (see CheckoutInput's doc comment).
        val buyer = currentActor.buyerOrNull()
        val total = discountedSubtotal + shippingFee

        // Snapshotted only when the seller had self-declared GST
        // registration at this exact moment — see Order.kt's doc comment.
        // AU retail prices are GST-inclusive by convention, so the GST
        // component of a GST-inclusive total is total / 11, not total * 0.1.
        val gstAmount = if (storeSettings?.gstRegistered == true) {
            BigDecimal(total).divide(BigDecimal(11), 0, RoundingMode.HALF_UP).toInt()
        } else {
            null
        }
        val sellerAbn = if (storeSettings?.gstRegistered == true) storeSettings.abn else null

        // The slowest item in the order sets the whole order's fulfillment/
        // delivery promise — resolved once here, while every Product row is
        // still guaranteed to exist, then snapshotted onto the Order (see
        // its doc comment on why this can't be a live join later).
        val fulfillmentTimeHours = resolvedItems.maxOf { (product, _, _) ->
            product.fulfillmentTimeHours ?: (storeSettings?.defaultFulfillmentTimeHours ?: 48)
        }
        val deliveryTimeHours = resolvedItems.maxOf { (product, _, _) ->
            product.deliveryTimeHours ?: (storeSettings?.defaultDeliveryTimeHours ?: 120)
        }

        val order = Order(
            orderNumber = generateOrderNumber(now, platformConfig.countryCode),
            store = store,
            subtotal = subtotal,
            fulfillmentTimeHours = fulfillmentTimeHours,
            deliveryTimeHours = deliveryTimeHours,
            deliveryMethod = deliveryMethod,
            shippingFee = shippingFee,
            platformFee = platformFee,
            total = total,
            couponCode = couponResolution?.code,
            discountAmount = discountAmount,
            sellerAbn = sellerAbn,
            gstAmount = gstAmount,
            status = OrderStatus.PENDING,
            paymentMethod = paymentMethod,
            paymentStatus = paymentStatus,
            shipping = ShippingDetails(
                fullName = input.shipping.fullName,
                phone = input.shipping.phone,
                // Normalized to null (never a blank/placeholder string) —
                // a pickup order has no address at all.
                addressLine1 = if (deliveryMethod == DeliveryMethod.PICKUP) null else input.shipping.addressLine1,
                city = if (deliveryMethod == DeliveryMethod.PICKUP) null else input.shipping.city,
                state = if (deliveryMethod == DeliveryMethod.PICKUP) null else input.shipping.state,
                postalCode = if (deliveryMethod == DeliveryMethod.PICKUP) null else input.shipping.postalCode,
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
        couponResolution?.let { couponService.recordUse(it.couponId) }

        orderNotifier.orderConfirmed(saved)
        orderNotifier.sellerOrderPlaced(saved)

        return publishOrderEvent(saved)
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
        val allowedNext = ALLOWED_STATUS_TRANSITIONS.getValue(order.status)
        if (status !in allowedNext) {
            throw ConflictException(
                "Order ${order.id} can't move from \"${order.status.wireValue}\" to \"${status.wireValue}\"",
            )
        }

        order.status = status
        if (status == OrderStatus.DELIVERED && order.paymentMethod == PaymentMethod.COD) {
            order.paymentStatus = PaymentStatus.PAID
        }
        if (status == OrderStatus.CANCELLED) {
            // Only PENDING/CONFIRMED ever reach CANCELLED (see
            // ALLOWED_STATUS_TRANSITIONS) — the goods were never shipped,
            // so the stock reserved at checkout (decrementStock) must come
            // back, or every cancellation permanently understates real
            // inventory.
            productService.restoreStock(order.items.map { it.productId to it.quantity })
        }
        if (status == OrderStatus.CANCELLED && order.paymentStatus == PaymentStatus.PAID) {
            // Stripe money actually has to move — refundPayment throws (and
            // rolls back this whole transaction) if the Stripe refund call
            // fails, rather than letting an order claim REFUNDED status
            // with no money actually returned. Every other payment
            // method's cancel behavior is unchanged.
            if (order.paymentMethod == PaymentMethod.STRIPE) {
                stripeService.refundPayment(order)
            }
            order.paymentStatus = PaymentStatus.REFUNDED
        }

        if (status == OrderStatus.SHIPPED) {
            val trackingNumber = input.trackingNumber?.trim()
            val courierServiceName = input.courierServiceName?.trim()
            require(!trackingNumber.isNullOrBlank()) { "Tracking number is required to mark an order as shipped" }
            require(!courierServiceName.isNullOrBlank()) { "Courier service name is required to mark an order as shipped" }
            order.trackingNumber = trackingNumber
            order.courierServiceName = courierServiceName
            // Starts the delivery-time clock — see Order.shippedAt's doc comment.
            order.shippedAt = Instant.now()
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
        return publishOrderEvent(saved)
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
        return publishOrderEvent(saved)
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
        return publishOrderEvent(saved)
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
        return publishOrderEvent(orderRepository.save(order))
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
