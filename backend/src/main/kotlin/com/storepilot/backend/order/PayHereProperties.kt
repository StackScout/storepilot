package com.storepilot.backend.order

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from PAYHERE_MERCHANT_ID / PAYHERE_MERCHANT_SECRET / PAYHERE_SANDBOX /
 * PAYHERE_NOTIFY_URL / PAYHERE_RETURN_URL_BASE env vars (application.yml).
 *
 * Uses the plain HTML-form redirect Checkout API, not the payhere.js onsite
 * popup SDK — the popup's `startPayment` readiness proved unreliable in
 * practice (async domain-validation step with no hard guarantee on timing).
 * The redirect method has no such step: submit a form, PayHere's own page
 * takes over from there.
 */
@ConfigurationProperties(prefix = "payhere")
data class PayHereProperties(
    val merchantId: String = "",
    val merchantSecret: String = "",
    val sandbox: Boolean = true,
    val notifyUrl: String = "http://localhost:8080/api/payments/payhere/notify",
    /** Buyer is redirected here (both on success and on cancel) as `{returnUrlBase}/{orderId}` — the order confirmation/tracking page, which reflects live status fetched from the backend. */
    val returnUrlBase: String = "http://localhost:3000/orders",
)
