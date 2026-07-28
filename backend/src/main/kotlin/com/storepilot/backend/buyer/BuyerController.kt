package com.storepilot.backend.buyer

import com.storepilot.backend.order.ShippingDetailsInput
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** The authenticated buyer's own profile — see BuyerService's doc comment for why there's no id/email-scoped lookup anymore. */
@RestController
class BuyerController(
    private val buyerService: BuyerService,
) {
    @GetMapping("/api/me")
    fun getCurrent(): BuyerResponse = buyerService.getCurrent()

    @PatchMapping("/api/me/default-shipping")
    fun updateDefaultShipping(@Valid @RequestBody input: ShippingDetailsInput): BuyerResponse =
        buyerService.updateDefaultShipping(input)
}
