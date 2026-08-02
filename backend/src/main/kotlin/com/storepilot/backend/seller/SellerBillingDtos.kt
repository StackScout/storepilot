package com.storepilot.backend.seller

import java.time.Instant

data class SellerPlanResponse(
    val plan: String,
    val currentPeriodEnd: Instant?,
    val cancelAtPeriodEnd: Boolean,
    /** Cents — the live platform_settings price, so the frontend never hardcodes it. */
    val monthlyPriceCents: Int,
    val currencyCode: String,
)

fun Seller.toPlanResponse(monthlyPriceCents: Int, currencyCode: String) = SellerPlanResponse(
    plan = plan.wireValue,
    currentPeriodEnd = planCurrentPeriodEnd,
    cancelAtPeriodEnd = planCancelAtPeriodEnd,
    monthlyPriceCents = monthlyPriceCents,
    currencyCode = currencyCode,
)

data class CheckoutUrlResponse(val checkoutUrl: String)
