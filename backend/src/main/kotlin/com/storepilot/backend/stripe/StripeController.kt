package com.storepilot.backend.stripe

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class StripeController(
    private val stripeConnectService: StripeConnectService,
    private val stripeWebhookService: StripeWebhookService,
    private val stripeService: StripeService,
) {
    @PostMapping("/api/stores/{storeId}/stripe-connect/onboard")
    fun startOnboarding(@PathVariable storeId: UUID): StripeOnboardingResponse =
        stripeConnectService.startOnboarding(storeId)

    /** POST /api/orders/{id}/stripe-checkout — guest-reachable, same as PayHere's equivalent (order id is the credential). */
    @PostMapping("/api/orders/{id}/stripe-checkout")
    fun checkout(@PathVariable id: UUID): StripeCheckoutSessionResponse = stripeService.createCheckoutSession(id)

    /**
     * Raw JSON body, not deserialized — Stripe's signature covers the exact
     * bytes it sent, so `@RequestBody String` (not a typed DTO) is required
     * here. The Stripe Dashboard endpoint backing this URL must be
     * configured to listen to **events on connected accounts** (see
     * StripeWebhookService's doc comment) — otherwise direct-charge events
     * for every seller's sale would silently never arrive here.
     */
    @PostMapping("/api/payments/stripe/webhook")
    fun webhook(
        @RequestBody rawPayload: String,
        @RequestHeader("Stripe-Signature") sigHeader: String,
    ): ResponseEntity<Void> {
        stripeWebhookService.handleWebhookEvent(rawPayload, sigHeader)
        return ResponseEntity.ok().build()
    }
}
