package com.storepilot.backend.order

import com.storepilot.backend.booking.Booking
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.booking.BookingStatus
import com.storepilot.backend.booking.BookingTimelineEntry
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
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
 * equivalent readiness gate to wait on). Order and Booking checkouts are
 * parallel sibling methods sharing the hash/amount helpers below — see
 * verifyAndApplyNotification's doc comment for why the webhook handler tries
 * both rather than a shared abstraction.
 */
@Service
class PayHereService(
    private val orderRepository: OrderRepository,
    private val bookingRepository: BookingRepository,
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

    /** POST /api/bookings/{id}/payhere-checkout — sibling to buildCheckoutPayload, reusing the same hash/format helpers. A booking has no shipping address, so address/city are blank (same as a pickup order's payload). */
    fun buildBookingCheckoutPayload(bookingId: UUID): PayHereCheckoutResponse {
        val booking = bookingRepository.findById(bookingId).orElseThrow { NotFoundException("Booking $bookingId not found") }
        if (booking.paymentMethod != PaymentMethod.PAYHERE) {
            throw ConflictException("Booking $bookingId is not a PayHere payment")
        }
        if (booking.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Booking $bookingId is already ${booking.paymentStatus.wireValue}")
        }

        val platformConfig = platformConfigService.current()
        val amount = formatAmount(booking.total)
        val currency = platformConfig.currencyCode
        val hash = generateHash(bookingId.toString(), amount, currency)

        val nameParts = booking.buyerName.trim().split(Regex("\\s+"), limit = 2).filter { it.isNotBlank() }
        val firstName = nameParts.getOrElse(0) { "Customer" }
        val lastName = nameParts.getOrElse(1) { firstName }

        val returnUrl = "${properties.bookingReturnUrlBase}/$bookingId"

        return PayHereCheckoutResponse(
            actionUrl = if (properties.sandbox) "https://sandbox.payhere.lk/pay/checkout" else "https://www.payhere.lk/pay/checkout",
            merchantId = properties.merchantId,
            orderId = bookingId.toString(),
            items = "${platformConfig.name} booking ${booking.bookingNumber}",
            amount = amount,
            currency = currency,
            hash = hash,
            notifyUrl = properties.notifyUrl,
            returnUrl = returnUrl,
            cancelUrl = returnUrl,
            firstName = firstName,
            lastName = lastName,
            email = booking.buyerEmail,
            phone = booking.buyerPhone,
            address = "",
            city = "",
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

        // order_id is PayHere's opaque merchant reference, round-tripped
        // verbatim — this platform hands it either an Order or a Booking id
        // depending on what was checked out, so try both. UUIDs are
        // independently random across the two tables (near-zero collision
        // risk, and a collision would just mean one lookup finds nothing).
        val id = runCatching { UUID.fromString(orderIdRaw) }.getOrNull()
        val order = id?.let { orderRepository.findById(it).orElse(null) }
        val booking = if (order == null) id?.let { bookingRepository.findById(it).orElse(null) } else null
        if (order == null && booking == null) {
            log.warn("PayHere notify: order/booking {} not found", orderIdRaw)
            return
        }

        val paymentId = params["payment_id"]
        val method = params["method"]
        val statusMessage = params["status_message"]
        val note = listOfNotNull(method?.let { "Method: $it" }, paymentId?.let { "PayHere payment ID: $it" })
            .joinToString(" · ").ifBlank { null }

        if (order != null) {
            when (statusCode) {
                "2" -> {
                    order.paymentStatus = PaymentStatus.PAID
                    if (order.status == OrderStatus.PENDING) order.status = OrderStatus.CONFIRMED
                    order.timeline.add(
                        OrderTimelineEntry(order = order, status = order.status, label = "Payment confirmed via PayHere", timestamp = Instant.now(), note = note),
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
        } else if (booking != null) {
            when (statusCode) {
                "2" -> {
                    booking.paymentStatus = PaymentStatus.PAID
                    if (booking.status == BookingStatus.PENDING) booking.status = BookingStatus.CONFIRMED
                    booking.timeline.add(
                        BookingTimelineEntry(booking = booking, status = booking.status, label = "Payment confirmed via PayHere", timestamp = Instant.now(), note = note),
                    )
                }
                "-1", "-2", "-3" -> {
                    booking.timeline.add(
                        BookingTimelineEntry(
                            booking = booking,
                            status = booking.status,
                            label = "PayHere payment ${if (statusCode == "-1") "cancelled" else "failed"}",
                            timestamp = Instant.now(),
                            note = statusMessage,
                        ),
                    )
                }
                else -> log.info("PayHere notify: booking {} status_code={} (pending)", orderIdRaw, statusCode)
            }
            bookingRepository.save(booking)
        }
    }

    private fun generateHash(orderId: String, amount: String, currency: String): String =
        md5Upper(properties.merchantId + orderId + amount + currency + md5Upper(properties.merchantSecret))

    private fun md5Upper(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.uppercase(Locale.ROOT)
    }

    /** [total] is cents (see Product.price's doc comment) — PayHere wants a decimal-string dollar amount. */
    private fun formatAmount(total: Int): String = BigDecimal(total).movePointLeft(2).toPlainString()
}
