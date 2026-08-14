package com.storepilot.backend.stripe

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from STRIPE_SECRET_KEY / STRIPE_PUBLISHABLE_KEY / STRIPE_WEBHOOK_SECRET
 * / STRIPE_SUCCESS_URL_BASE / STRIPE_CANCEL_URL_BASE env vars (application.yml).
 *
 * Stripe Connect, Standard accounts, direct charges — see StripeConnectService
 * and StripeService's doc comments for what that means. [webhookSecret] is
 * for a webhook endpoint specifically configured (in the Stripe Dashboard)
 * to listen to **events on connected accounts**, not just the platform's own
 * account — everything this integration cares about (checkout.session.*,
 * account.updated) is a connected-account event for a direct charge.
 */
@ConfigurationProperties(prefix = "stripe")
data class StripeProperties(
    val secretKey: String = "",
    val publishableKey: String = "",
    val webhookSecret: String = "",
    /** Buyer lands back here as `{successUrlBase}/{orderId}` / `{cancelUrlBase}/{orderId}` — the order page, same pattern as PayHereProperties.returnUrlBase. */
    val successUrlBase: String = "http://localhost:3000/orders",
    val cancelUrlBase: String = "http://localhost:3000/orders",
    /** Same shape as [successUrlBase]/[cancelUrlBase], for a booking checkout instead of an order — see StripeService.createBookingCheckoutSession. */
    val bookingSuccessUrlBase: String = "http://localhost:3000/bookings",
    val bookingCancelUrlBase: String = "http://localhost:3000/bookings",
    /**
     * Seller Pro-plan billing (see SellerBillingService/SellerBillingWebhookService)
     * — a separate Dashboard webhook endpoint/secret from [webhookSecret]
     * above, since it listens to "Your account" events, not "Connected
     * accounts". Both success/cancel land back on the settings page (unlike
     * the order flow's per-order URL, there's only one place a seller
     * manages their plan).
     */
    val billingWebhookSecret: String = "",
    val billingSuccessUrlBase: String = "http://localhost:3000/dashboard/settings",
    val billingCancelUrlBase: String = "http://localhost:3000/dashboard/settings",
)
