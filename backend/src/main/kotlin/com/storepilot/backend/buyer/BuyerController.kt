package com.storepilot.backend.buyer

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** The authenticated buyer's own profile — see BuyerService's doc comment for why there's no id/email-scoped lookup anymore. Saved addresses: see AddressController. */
@RestController
class BuyerController(
    private val buyerService: BuyerService,
) {
    @GetMapping("/api/me")
    fun getCurrent(): BuyerResponse = buyerService.getCurrent()
}
