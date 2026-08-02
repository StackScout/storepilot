package com.storepilot.backend.admin

import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.wireValueOf
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Gated by SecurityConfig's hasRole("ADMIN") on the /api/admin prefix. */
@RestController
class AuditLogController(
    private val auditLogService: AuditLogService,
) {
    @GetMapping("/api/admin/audit-log")
    fun list(
        @RequestParam action: String?,
        @RequestParam targetType: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
    ): PageResponse<AuditLogResponse> =
        auditLogService.list(action?.let { wireValueOf<AuditAction>(it) }, targetType, page, size)
}
