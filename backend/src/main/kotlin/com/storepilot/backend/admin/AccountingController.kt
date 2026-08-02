package com.storepilot.backend.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Gated by SecurityConfig's hasRole("ADMIN") on the /api/admin prefix. */
@RestController
class AccountingController(
    private val accountingService: AccountingService,
) {
    @GetMapping("/api/admin/accounting/summary")
    fun summary(): AccountingSummaryResponse = accountingService.summary()
}
