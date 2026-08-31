package com.storepilot.backend.notification

import com.storepilot.backend.common.PageResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matched by permissions.yml's broad /api/me path rule (BUYER) — no new matcher needed, see SellerNotificationController's identical doc comment. */
@RestController
class BuyerNotificationController(
    private val buyerNotificationService: BuyerNotificationService,
) {
    @GetMapping("/api/me/buyer/notifications")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<BuyerNotificationResponse> = buyerNotificationService.list(page, size)

    @GetMapping("/api/me/buyer/notifications/summary")
    fun summary(): BuyerNotificationSummaryResponse = buyerNotificationService.summary()

    @PatchMapping("/api/me/buyer/notifications/{id}/read")
    fun markRead(@PathVariable id: UUID): BuyerNotificationResponse = buyerNotificationService.markRead(id)

    @PatchMapping("/api/me/buyer/notifications/read-all")
    fun markAllRead() = buyerNotificationService.markAllRead()
}
