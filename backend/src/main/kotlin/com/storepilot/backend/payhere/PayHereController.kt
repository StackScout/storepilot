package com.storepilot.backend.payhere

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PayHereController(
    private val payHereService: PayHereService,
) {
    @PostMapping("/api/orders/{id}/payhere-checkout")
    fun checkout(@PathVariable id: UUID): PayHereCheckoutResponse = payHereService.buildCheckoutPayload(id)

    @PostMapping("/api/bookings/{id}/payhere-checkout")
    fun bookingCheckout(@PathVariable id: UUID): PayHereCheckoutResponse = payHereService.buildBookingCheckoutPayload(id)

    /**
     * PayHere's webhook posts application/x-www-form-urlencoded, not JSON.
     * Must be reachable on a public IP/domain — PayHere will never call
     * localhost, so local sandbox testing needs a tunnel (e.g. ngrok) with
     * PAYHERE_NOTIFY_URL pointed at it.
     */
    @PostMapping("/api/payments/payhere/notify", consumes = ["application/x-www-form-urlencoded"])
    fun notify(@RequestParam params: Map<String, String>): ResponseEntity<Void> {
        payHereService.verifyAndApplyNotification(params)
        return ResponseEntity.ok().build()
    }
}
