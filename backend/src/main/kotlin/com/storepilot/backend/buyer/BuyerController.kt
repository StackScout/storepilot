package com.storepilot.backend.buyer

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/** The authenticated buyer's own profile — see BuyerService's doc comment for why there's no id/email-scoped lookup anymore. Saved addresses: see AddressController. */
@RestController
class BuyerController(
    private val buyerService: BuyerService,
    private val buyerAccountService: BuyerAccountService,
    private val buyerExportService: BuyerExportService,
) {
    @GetMapping("/api/me")
    fun getCurrent(): BuyerResponse = buyerService.getCurrent()

    /** GET /api/me/export — see BuyerExportService's doc comment. */
    @GetMapping("/api/me/export")
    fun exportCurrent(): BuyerExportResponse = buyerExportService.exportCurrentBuyer()

    /** POST /api/me/delete — see BuyerAccountService.deleteCurrentBuyer's doc comment. */
    @PostMapping("/api/me/delete")
    fun deleteCurrent(): ResponseEntity<Void> {
        buyerAccountService.deleteCurrentBuyer()
        return ResponseEntity.noContent().build()
    }
}
