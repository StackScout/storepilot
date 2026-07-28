package com.storepilot.backend.stripe

/** POST /api/stores/{id}/stripe-connect/onboard — redirect the seller's browser here. */
data class StripeOnboardingResponse(
    val onboardingUrl: String,
)

/** POST /api/orders/{id}/stripe-checkout — redirect the buyer's browser here. */
data class StripeCheckoutSessionResponse(
    val checkoutUrl: String,
)
