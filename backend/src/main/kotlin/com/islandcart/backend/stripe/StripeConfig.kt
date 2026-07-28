package com.islandcart.backend.stripe

import com.stripe.Stripe
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/** Sets the SDK's global API key once at startup — this platform has exactly one Stripe account (secret key), never per-tenant, so the classic static-resource-method style (Account.create(...), Session.create(...), etc.) is simpler than juggling a StripeClient instance everywhere. */
@Component
class StripeConfig(private val stripeProperties: StripeProperties) {
    @PostConstruct
    fun init() {
        Stripe.apiKey = stripeProperties.secretKey
    }
}
