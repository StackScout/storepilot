package com.storepilot.backend.stripe

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.order.OrderStatus
import com.storepilot.backend.order.OrderTimelineEntry
import com.storepilot.backend.order.PaymentMethod
import com.storepilot.backend.order.PaymentStatus
import com.storepilot.backend.store.StoreSettingsRepository
import com.stripe.model.Refund
import com.stripe.model.checkout.Session
import com.stripe.net.RequestOptions
import com.stripe.param.RefundCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Checkout + refund via Stripe Connect **direct charges** — the Checkout
 * Session (and the resulting charge) is created *on the seller's connected
 * account itself* (the `Stripe-Account` request-option header below), not
 * on the platform's account with funds transferred out afterward. This is
 * what makes the charge genuinely belong to the seller, and what makes
 * `application_fee_amount` the platform's entire involvement in the money —
 * no custody, ever. See StripeConnectService for the onboarding side.
 */
@Service
class StripeService(
    private val orderRepository: OrderRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val platformConfigService: PlatformConfigService,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StripeService::class.java)

    /** POST /api/orders/{id}/stripe-checkout */
    fun createCheckoutSession(orderId: UUID): StripeCheckoutSessionResponse {
        val order = orderRepository.findById(orderId).orElseThrow { NotFoundException("Order $orderId not found") }
        if (order.paymentMethod != PaymentMethod.STRIPE) {
            throw ConflictException("Order $orderId is not a Stripe payment")
        }
        if (order.paymentStatus != PaymentStatus.UNPAID) {
            throw ConflictException("Order $orderId is already ${order.paymentStatus.wireValue}")
        }
        val storeId = requireNotNull(order.store.id)
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId")
        }
        val accountId = settings.stripeAccountId
        if (accountId == null || !settings.stripeChargesEnabled) {
            throw ConflictException("Store $storeId is not ready to accept Stripe payments")
        }

        val platformConfig = platformConfigService.current()
        val requestOptions = RequestOptions.builder().setStripeAccount(accountId).build()

        // Single line item for the whole order total — same "one amount,
        // descriptive item text" approach PayHereService uses, avoiding any
        // per-product rounding drift.
        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(
                        SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(platformConfig.currencyCode.lowercase())
                            .setUnitAmount(order.total.toLong())
                            .setProductData(
                                SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName("${platformConfig.name} order ${order.orderNumber}")
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            // The platform's entire take from this sale — Stripe deducts it
            // automatically at charge time, no separate transfer step.
            .setPaymentIntentData(
                SessionCreateParams.PaymentIntentData.builder()
                    .setApplicationFeeAmount(order.platformFee.toLong())
                    .build(),
            )
            .setClientReferenceId(orderId.toString())
            .setCustomerEmail(order.buyerEmail)
            .setSuccessUrl("${stripeProperties.successUrlBase}/$orderId")
            .setCancelUrl("${stripeProperties.cancelUrlBase}/$orderId")
            .build()

        val session = Session.create(params, requestOptions)
        return StripeCheckoutSessionResponse(checkoutUrl = session.url)
    }

    /** Called by StripeWebhookService for `checkout.session.completed`. */
    @Transactional
    fun handleCheckoutSessionCompleted(session: Session) {
        val order = findOrderByClientReferenceId(session.clientReferenceId) ?: return
        // Idempotency — Stripe redelivers webhooks; only ever apply this once.
        if (order.paymentStatus != PaymentStatus.UNPAID) {
            log.info("Stripe checkout.session.completed: order {} already {}, ignoring redelivery", order.orderNumber, order.paymentStatus.wireValue)
            return
        }
        order.paymentStatus = PaymentStatus.PAID
        if (order.status == OrderStatus.PENDING) order.status = OrderStatus.CONFIRMED
        order.stripePaymentIntentId = session.paymentIntent
        order.timeline.add(
            OrderTimelineEntry(order = order, status = order.status, label = "Payment confirmed via Stripe", timestamp = Instant.now()),
        )
        orderRepository.save(order)
    }

    /** Called by StripeWebhookService for `checkout.session.expired` / `checkout.session.async_payment_failed` — order stays unpaid/pending so a fresh session can be created later. */
    @Transactional
    fun handleCheckoutSessionFailed(session: Session, label: String) {
        val order = findOrderByClientReferenceId(session.clientReferenceId) ?: return
        order.timeline.add(
            OrderTimelineEntry(order = order, status = order.status, label = label, timestamp = Instant.now()),
        )
        orderRepository.save(order)
    }

    private fun findOrderByClientReferenceId(clientReferenceId: String?): Order? {
        val orderId = clientReferenceId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val order = orderId?.let { orderRepository.findById(it).orElse(null) }
        if (order == null) {
            log.warn("Stripe webhook: order {} not found", clientReferenceId)
        }
        return order
    }

    /**
     * Called from OrderService.updateStatus when a paid Stripe order is
     * cancelled. Must succeed before the caller marks the order REFUNDED —
     * throws (rolling back the whole status update) rather than letting an
     * order claim to be refunded when no money actually moved.
     * `refund_application_fee = true` so the platform's own cut is given
     * back too, since the sale is being fully undone.
     */
    fun refundPayment(order: Order) {
        val paymentIntentId = order.stripePaymentIntentId
            ?: throw ConflictException("Order ${order.id} has no Stripe payment to refund")
        val storeId = requireNotNull(order.store.id)
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId")
        }
        val accountId = settings.stripeAccountId
            ?: throw ConflictException("Store $storeId has no connected Stripe account")

        Refund.create(
            RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setRefundApplicationFee(true)
                .build(),
            RequestOptions.builder().setStripeAccount(accountId).build(),
        )
    }
}
