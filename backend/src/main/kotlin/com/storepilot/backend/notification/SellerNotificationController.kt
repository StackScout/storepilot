package com.storepilot.backend.notification

import com.storepilot.backend.common.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matched by SecurityConfig's existing /api/me/seller wildcard rule -> hasRole("SELLER") — no new security matcher needed, see PushTokenController's identical doc comment. */
@RestController
class SellerNotificationController(
    private val sellerNotificationService: SellerNotificationService,
) {
    @GetMapping("/api/me/seller/notifications")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<SellerNotificationResponse> = sellerNotificationService.list(page, size)

    @GetMapping("/api/me/seller/notifications/summary")
    fun summary(): SellerNotificationSummaryResponse = sellerNotificationService.summary()

    @PatchMapping("/api/me/seller/notifications/{id}/read")
    fun markRead(@PathVariable id: UUID): SellerNotificationResponse = sellerNotificationService.markRead(id)

    @PatchMapping("/api/me/seller/notifications/read-all")
    fun markAllRead() = sellerNotificationService.markAllRead()
}
