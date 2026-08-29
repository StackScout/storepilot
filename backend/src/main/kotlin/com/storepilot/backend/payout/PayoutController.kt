package com.storepilot.backend.payout

import com.storepilot.backend.booking.BookingResponse
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.order.OrderResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#payouts and #admin. */
@RestController
class PayoutController(
    private val payoutService: PayoutService,
) {
    @GetMapping("/api/stores/{storeId}/payouts")
    fun listByStore(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<PayoutResponse> = payoutService.listByStore(storeId, page, size)

    @GetMapping("/api/stores/{storeId}/payouts/eligible-orders")
    fun eligibleOrders(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = payoutService.getEligibleOrders(storeId, page, size)

    @GetMapping("/api/stores/{storeId}/payouts/eligible-bookings")
    fun eligibleBookings(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<BookingResponse> = payoutService.getEligibleBookings(storeId, page, size)

    /** Admin-only — see PayoutService.adminGetEligibleOrders's doc comment. */
    @GetMapping("/api/admin/stores/{storeId}/payouts/eligible-orders")
    fun adminEligibleOrders(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = payoutService.adminGetEligibleOrders(storeId, page, size)

    /** Admin-only — see PayoutService.adminGetEligibleBookings's doc comment. */
    @GetMapping("/api/admin/stores/{storeId}/payouts/eligible-bookings")
    fun adminEligibleBookings(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<BookingResponse> = payoutService.adminGetEligibleBookings(storeId, page, size)

    @PostMapping("/api/admin/stores/{storeId}/payouts")
    fun createBatch(@PathVariable storeId: UUID): ResponseEntity<PayoutResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(payoutService.createBatch(storeId))

    @GetMapping("/api/admin/payouts")
    fun adminList(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<PayoutResponse> = payoutService.adminList(page, size)

    @PatchMapping("/api/admin/payouts/{payoutId}")
    fun markPaid(
        @PathVariable payoutId: UUID,
        @RequestBody input: MarkPaidInput,
    ): PayoutResponse = payoutService.markPaid(payoutId, input)
}
