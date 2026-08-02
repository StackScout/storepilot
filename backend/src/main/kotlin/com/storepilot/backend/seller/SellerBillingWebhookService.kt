package com.storepilot.backend.seller

import com.storepilot.backend.stripe.StripeProperties
import com.storepilot.backend.stripe.deserializeStripeEvent
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Webhook for seller Pro-plan subscription events — a **separate** Stripe
 * Dashboard endpoint and signing secret from StripeWebhookService's order-
 * payment webhook. That one is configured to listen to "Connected
 * accounts" events (every seller's own account); this one must be
 * configured to listen to "Your account" events, since a seller's
 * Subscription lives on the platform's own Stripe account, not a connected
 * one — mixing the two scopes under one endpoint is exactly the kind of
 * webhook-configuration mistake that cost real debugging time on the
 * Connect side earlier (see StripeWebhookService's history), so this is
 * deliberately kept as its own endpoint/secret rather than reusing that one.
 */
@Service
class SellerBillingWebhookService(
    private val stripeProperties: StripeProperties,
    private val sellerBillingService: SellerBillingService,
) {
    private val log = LoggerFactory.getLogger(SellerBillingWebhookService::class.java)

    fun handleWebhookEvent(rawPayload: String, sigHeader: String) {
        val event = try {
            Webhook.constructEvent(rawPayload, sigHeader, stripeProperties.billingWebhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe billing webhook: signature verification failed — ignoring (possible forged callback)", e)
            return
        }

        when (event.type) {
            "checkout.session.completed" -> {
                val session = deserializeStripeEvent(event) as? Session
                if (session == null) {
                    log.warn("Stripe billing webhook: checkout.session.completed with no deserializable Session payload, ignoring")
                    return
                }
                sellerBillingService.handleCheckoutCompleted(session)
            }
            "customer.subscription.updated", "customer.subscription.deleted" -> {
                val subscription = deserializeStripeEvent(event) as? Subscription
                if (subscription == null) {
                    log.warn("Stripe billing webhook: {} with no deserializable Subscription payload, ignoring", event.type)
                    return
                }
                sellerBillingService.handleSubscriptionEvent(subscription)
            }
            else -> log.info("Stripe billing webhook: unhandled event type {} (id={}), ignoring", event.type, event.id)
        }
    }
}
