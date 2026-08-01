package com.storepilot.backend.stripe

import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Account
import com.stripe.model.Event
import com.stripe.model.StripeObject
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Single webhook endpoint for every Stripe event this platform cares about —
 * all of them Connect-scoped (fired by a *connected account*, since every
 * charge is a direct charge on the seller's own account, not the
 * platform's) — see the Dashboard-configuration note in StripeController.
 * Verifies the signature once, then dispatches by event type.
 */
@Service
class StripeWebhookService(
    private val stripeProperties: StripeProperties,
    private val stripeConnectService: StripeConnectService,
    private val stripeService: StripeService,
) {
    private val log = LoggerFactory.getLogger(StripeWebhookService::class.java)

    /**
     * `event.dataObjectDeserializer.getObject()` only succeeds when the
     * event's `api_version` matches the `stripe-java` SDK's own pinned API
     * version — otherwise it silently returns empty, even though the
     * payload itself is perfectly valid (confirmed live: an `acct_...`'s
     * default API version drifted ahead of this SDK's, e.g.
     * `2026-06-24.dahlia`, and every account.updated since then landed here
     * empty). `deserializeUnsafe()` is Stripe's own documented fallback for
     * this exact mismatch — it deserializes against the SDK's model classes
     * regardless of version, which is safe for the stable, long-lived
     * fields this app actually reads (chargesEnabled, payoutsEnabled, a
     * checkout Session's id/paymentStatus/metadata, ...). The real fix is
     * keeping `stripe-java` reasonably current; this is the belt-and-braces
     * fallback for whenever it inevitably drifts again anyway.
     */
    private fun deserializeEventObject(event: Event): StripeObject? {
        val deserializer = event.dataObjectDeserializer
        deserializer.getObject().let { if (it.isPresent) return it.get() }
        return try {
            deserializer.deserializeUnsafe()
        } catch (e: Exception) {
            log.warn(
                "Stripe webhook: {} (event {}) — payload deserialization failed even with deserializeUnsafe(), ignoring",
                event.type,
                event.id,
                e,
            )
            null
        }
    }

    fun handleWebhookEvent(rawPayload: String, sigHeader: String) {
        val event = try {
            Webhook.constructEvent(rawPayload, sigHeader, stripeProperties.webhookSecret)
        } catch (e: SignatureVerificationException) {
            log.warn("Stripe webhook: signature verification failed — ignoring (possible forged callback)", e)
            return
        }

        when (event.type) {
            "account.updated" -> {
                val account = deserializeEventObject(event) as? Account
                if (account == null) {
                    log.warn("Stripe webhook: account.updated with no deserializable Account payload, ignoring")
                    return
                }
                stripeConnectService.syncAccountStatus(account)
            }
            "checkout.session.completed" -> {
                val session = deserializeEventObject(event) as? Session
                if (session == null) {
                    log.warn("Stripe webhook: checkout.session.completed with no deserializable Session payload, ignoring")
                    return
                }
                stripeService.handleCheckoutSessionCompleted(session)
            }
            "checkout.session.expired" -> {
                val session = deserializeEventObject(event) as? Session ?: return
                stripeService.handleCheckoutSessionFailed(session, "Stripe payment session expired")
            }
            "checkout.session.async_payment_failed" -> {
                val session = deserializeEventObject(event) as? Session ?: return
                stripeService.handleCheckoutSessionFailed(session, "Stripe payment failed")
            }
            else -> log.info("Stripe webhook: unhandled event type {} (id={}), ignoring", event.type, event.id)
        }
    }
}
