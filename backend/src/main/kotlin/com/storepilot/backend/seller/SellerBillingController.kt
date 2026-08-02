package com.storepilot.backend.seller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController

@RestController
class SellerBillingController(
    private val sellerBillingService: SellerBillingService,
    private val sellerBillingWebhookService: SellerBillingWebhookService,
) {
    /** GET /api/me/seller/plan */
    @GetMapping("/api/me/seller/plan")
    fun currentPlan(): SellerPlanResponse = sellerBillingService.currentPlan()

    /** POST /api/me/seller/billing/checkout — returns a Stripe Checkout URL (subscription mode) to redirect the browser to. */
    @PostMapping("/api/me/seller/billing/checkout")
    fun startCheckout(): CheckoutUrlResponse = sellerBillingService.startCheckout()

    /** POST /api/me/seller/billing/cancel */
    @PostMapping("/api/me/seller/billing/cancel")
    fun cancel(): SellerPlanResponse = sellerBillingService.cancelAtPeriodEnd()

    /** POST /api/me/seller/billing/refresh — see SellerBillingService.refreshFromStripe's doc comment. */
    @PostMapping("/api/me/seller/billing/refresh")
    fun refresh(): SellerPlanResponse = sellerBillingService.refreshFromStripe()

    /** Raw JSON body, not deserialized — see StripeController's webhook doc comment for why. This Dashboard endpoint must listen to "Your account" events, not "Connected accounts" — see SellerBillingWebhookService's doc comment. */
    @PostMapping("/api/billing/stripe/webhook")
    fun webhook(
        @RequestBody rawPayload: String,
        @RequestHeader("Stripe-Signature") sigHeader: String,
    ): ResponseEntity<Void> {
        sellerBillingWebhookService.handleWebhookEvent(rawPayload, sigHeader)
        return ResponseEntity.ok().build()
    }
}
