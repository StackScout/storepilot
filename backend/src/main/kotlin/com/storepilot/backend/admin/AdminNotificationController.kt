package com.storepilot.backend.admin

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** All gated by SecurityConfig's hasRole("ADMIN") on the admin path prefix. */
@RestController
class AdminNotificationController(
    private val adminNotificationService: AdminNotificationService,
) {
    @GetMapping("/api/admin/notifications")
    fun list(): List<AdminNotificationResponse> = adminNotificationService.list()

    @GetMapping("/api/admin/notifications/summary")
    fun summary(): AdminNotificationSummaryResponse = adminNotificationService.summary()

    @PatchMapping("/api/admin/notifications/{id}/read")
    fun markRead(@PathVariable id: UUID): AdminNotificationResponse = adminNotificationService.markRead(id)

    @PatchMapping("/api/admin/notifications/read-all")
    fun markAllRead() = adminNotificationService.markAllRead()
}
