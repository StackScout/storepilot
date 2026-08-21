package com.storepilot.backend.seller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SellerAccountController(
    private val sellerAccountService: SellerAccountService,
    private val sellerExportService: SellerExportService,
) {
    /** GET /api/me/seller/export — see SellerExportService's doc comment. */
    @GetMapping("/api/me/seller/export")
    fun exportCurrent(): SellerExportResponse = sellerExportService.exportCurrentSeller()

    /** POST /api/me/seller/delete — see SellerAccountService.deleteCurrentSeller's doc comment. */
    @PostMapping("/api/me/seller/delete")
    fun deleteCurrent(): ResponseEntity<Void> {
        sellerAccountService.deleteCurrentSeller()
        return ResponseEntity.noContent().build()
    }
}
