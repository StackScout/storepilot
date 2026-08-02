package com.storepilot.backend.stripe

import com.stripe.model.Event
import com.stripe.model.StripeObject
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("com.storepilot.backend.stripe.StripeEventDeserializer")

/**
 * `event.dataObjectDeserializer.getObject()` only succeeds when the event's
 * `api_version` matches the `stripe-java` SDK's own pinned API version —
 * otherwise it silently returns empty, even though the payload itself is
 * perfectly valid (confirmed live: an account's default API version
 * drifted ahead of this SDK's, e.g. `2026-06-24.dahlia`, and every
 * account.updated since then landed empty — see StripeWebhookService's
 * original bug writeup). `deserializeUnsafe()` is Stripe's own documented
 * fallback for this exact mismatch — it deserializes against the SDK's
 * model classes regardless of version, which is safe for the stable,
 * long-lived fields this app actually reads. The real fix is keeping
 * `stripe-java` reasonably current; this is the belt-and-braces fallback
 * for whenever it inevitably drifts again. Shared by every webhook
 * receiver in this app (order-payment events on connected accounts, and
 * seller-billing subscription events on the platform's own account) since
 * the failure mode and fix are identical regardless of which Stripe
 * account the event came from.
 */
fun deserializeStripeEvent(event: Event): StripeObject? {
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
