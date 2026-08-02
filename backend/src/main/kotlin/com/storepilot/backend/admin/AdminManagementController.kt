package com.storepilot.backend.admin

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Gated by SecurityConfig's hasRole("ADMIN") on the /api/admin prefix — inviting another admin is itself an admin-only action. */
@RestController
class AdminManagementController(
    private val adminManagementService: AdminManagementService,
) {
    @PostMapping("/api/admin/admins")
    fun invite(@Valid @RequestBody input: InviteAdminInput): InviteAdminResult = adminManagementService.invite(input)

    @GetMapping("/api/admin/admins")
    fun list(): List<AdminSummaryResponse> = adminManagementService.list()
}
