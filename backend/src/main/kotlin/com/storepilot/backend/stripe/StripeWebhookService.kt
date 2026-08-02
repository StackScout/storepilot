package com.storepilot.backend.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Account
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Single webhook endpoint for every Stripe event this platform cares about —
 * all of them Connect-scoped (fired by a *connected account*, since every
 * charge is a direct charge on the seller's own account, not the
 * platform's) — see the Dashboard-configuration note in StripeController.
 * Verifies the signature once, then dispatches by event type. See
 * SellerBillingWebhookService for the separate webhook (different Stripe
 * Dashboard endpoint, different signing secret) for seller Pro-plan
 * subscription events on the platform's own account — deserializeStripeEvent
 * (see StripeEventDeserializer.kt) is shared by both.
 */
@Service
class StripeWebhookService(
    private val stripeProperties: StripeProperties,
    private val stripeConnectService: StripeConnectService,
    private val stripeService: StripeService,
) {
    private val log = LoggerFactory.getLogger(StripeWebhookService::class.java)

    fun handleWebhookEvent(rawPayload: String, sigHeader: String) {
        val event = try {
            Webhook.constructEvent(rawPayload, sigHeader, stripeProperties.webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe webhook: signature verification failed — ignoring (possible forged callback)", e)
            return
        }

        when (event.type) {
            "account.updated" -> {
                val account = deserializeStripeEvent(event) as? Account
                if (account == null) {
                    log.warn("Stripe webhook: account.updated with no deserializable Account payload, ignoring")
                    return
                }
                stripeConnectService.syncAccountStatus(account)
            }
            "checkout.session.completed" -> {
                val session = deserializeStripeEvent(event) as? Session
                if (session == null) {
                    log.warn("Stripe webhook: checkout.session.completed with no deserializable Session payload, ignoring")
                    return
                }
                stripeService.handleCheckoutSessionCompleted(session)
            }
            "checkout.session.expired" -> {
                val session = deserializeStripeEvent(event) as? Session ?: return
                stripeService.handleCheckoutSessionFailed(session, "Stripe payment session expired")
            }
            "checkout.session.async_payment_failed" -> {
                val session = deserializeStripeEvent(event) as? Session ?: return
                stripeService.handleCheckoutSessionFailed(session, "Stripe payment failed")
            }
            else -> log.info("Stripe webhook: unhandled event type {} (id={}), ignoring", event.type, event.id)
        }
    }
}
