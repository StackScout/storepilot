package com.islandcart.backend.order

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.PlatformConfigService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID

/**
 * See https://support.payhere.lk/api-&-mobile-sdk/checkout-api for the
 * integration this implements — a plain HTML form POST redirect to PayHere's
 * own gateway page, not the payhere.js onsite popup SDK (the popup's async
 * `startPayment` readiness proved unreliable; the redirect method has no
 * equivalent readiness gate to wait on).
 */
@Service
class PayHereService(
    private val orderRepository: OrderRepository,
    private val properties: PayHereProperties,
    private val platformConfigService: PlatformConfigService,
) {
    private val log = LoggerFactory.getLogger(PayHereService::class.java)

    /** POST /api/orders/{id}/payhere-checkout */
    fun buildCheckoutPayload(orderId: UUID): PayHereCheckoutResponse {
        val order = orderRepository.findById(orderId).orElseThrow { NotFoundException("Order $orderId not found") }
        if (order.paymentMethod != PaymentMethod.PAYHERE) {
            throw ConflictException("Order $orderId is not a PayHere payment")
        }
        if (order.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Order $orderId is already ${order.paymentStatus.wireValue}")
        }

        val platformConfig = platformConfigService.current()
        val amount = formatAmount(order.total)
        val currency = platformConfig.currencyCode
        val hash = generateHash(orderId.toString(), amount, currency)

        // ShippingDetails only has one combined fullName field; PayHere wants
        // first/last split separately.
        val nameParts = order.shipping.fullName?.trim()?.split(Regex("\\s+"), limit = 2)
            ?.filter { it.isNotBlank() } ?: emptyList()
        val firstName = nameParts.getOrElse(0) { "Customer" }
        val lastName = nameParts.getOrElse(1) { firstName }

        // Buyer lands back here (success or cancel alike) — the order page
        // fetches live status from the backend, updated by the notify webhook.
        val returnUrl = "${properties.returnUrlBase}/$orderId"

        return PayHereCheckoutResponse(
            actionUrl = if (properties.sandbox) "https://sandbox.payhere.lk/pay/checkout" else "https://www.payhere.lk/pay/checkout",
            merchantId = properties.merchantId,
            orderId = orderId.toString(),
            items = "${platformConfig.name} order ${order.orderNumber}",
            amount = amount,
            currency = currency,
            hash = hash,
            notifyUrl = properties.notifyUrl,
            returnUrl = returnUrl,
            cancelUrl = returnUrl,
            firstName = firstName,
            lastName = lastName,
            email = order.buyerEmail,
            phone = order.shipping.phone ?: "",
            address = order.shipping.addressLine1 ?: "",
            city = order.shipping.city ?: "",
            country = platformConfig.countryName,
        )
    }

    /**
     * POST /api/payments/payhere/notify — PayHere's server-to-server webhook.
     * Always returns normally (2xx) regardless of outcome; there's no
     * "reject this notification" response PayHere understands. A bad/forged
     * signature is just logged and ignored, never applied.
     */
    @Transactional
    fun verifyAndApplyNotification(params: Map<String, String>) {
        val merchantId = params["merchant_id"]
        val orderIdRaw = params["order_id"]
        val payhereAmount = params["payhere_amount"]
        val payhereCurrency = params["payhere_currency"]
        val statusCode = params["status_code"]
        val md5sig = params["md5sig"]
        if (merchantId == null || orderIdRaw == null || payhereAmount == null || payhereCurrency == null || statusCode == null || md5sig == null) {
            log.warn("PayHere notify: missing required params, ignoring: {}", params.keys)
            return
        }

        val expected = md5Upper(merchantId + orderIdRaw + payhereAmount + payhereCurrency + statusCode + md5Upper(properties.merchantSecret))
        if (expected != md5sig) {
            log.warn("PayHere notify: md5sig mismatch for order {} — ignoring (possible forged callback)", orderIdRaw)
            return
        }

        val orderId = runCatching { UUID.fromString(orderIdRaw) }.getOrNull()
        val order = orderId?.let { orderRepository.findById(it).orElse(null) }
        if (order == null) {
            log.warn("PayHere notify: order {} not found", orderIdRaw)
            return
        }

        val paymentId = params["payment_id"]
        val method = params["method"]
        val statusMessage = params["status_message"]

        when (statusCode) {
            "2" -> {
                order.paymentStatus = PaymentStatus.PAID
                if (order.status == OrderStatus.PENDING) order.status = OrderStatus.CONFIRMED
                order.timeline.add(
                    OrderTimelineEntry(
                        order = order,
                        status = order.status,
                        label = "Payment confirmed via PayHere",
                        timestamp = Instant.now(),
                        note = listOfNotNull(method?.let { "Method: $it" }, paymentId?.let { "PayHere payment ID: $it" })
                            .joinToString(" · ").ifBlank { null },
                    ),
                )
            }
            "-1", "-2", "-3" -> {
                order.timeline.add(
                    OrderTimelineEntry(
                        order = order,
                        status = order.status,
                        label = "PayHere payment ${if (statusCode == "-1") "cancelled" else "failed"}",
                        timestamp = Instant.now(),
                        note = statusMessage,
                    ),
                )
            }
            else -> log.info("PayHere notify: order {} status_code={} (pending)", orderIdRaw, statusCode)
        }
        orderRepository.save(order)
    }

    private fun generateHash(orderId: String, amount: String, currency: String): String =
        md5Upper(properties.merchantId + orderId + amount + currency + md5Upper(properties.merchantSecret))

    private fun md5Upper(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.uppercase(Locale.ROOT)
    }

    private fun formatAmount(total: Int): String = BigDecimal(total).setScale(2).toPlainString()
}
