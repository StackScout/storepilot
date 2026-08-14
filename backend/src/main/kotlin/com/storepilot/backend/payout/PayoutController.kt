package com.storepilot.backend.payout

import com.storepilot.backend.booking.BookingResponse
import com.storepilot.backend.order.OrderResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#payouts and #admin. */
@RestController
class PayoutController(
    private val payoutService: PayoutService,
) {
    @GetMapping("/api/stores/{storeId}/payouts")
    fun listByStore(@PathVariable storeId: UUID): List<PayoutResponse> = payoutService.listByStore(storeId)

    @GetMapping("/api/stores/{storeId}/payouts/eligible-orders")
    fun eligibleOrders(@PathVariable storeId: UUID): List<OrderResponse> = payoutService.getEligibleOrders(storeId)

    @GetMapping("/api/stores/{storeId}/payouts/eligible-bookings")
    fun eligibleBookings(@PathVariable storeId: UUID): List<BookingResponse> = payoutService.getEligibleBookings(storeId)

    @PostMapping("/api/admin/stores/{storeId}/payouts")
    fun createBatch(@PathVariable storeId: UUID): ResponseEntity<PayoutResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(payoutService.createBatch(storeId))

    @GetMapping("/api/admin/payouts")
    fun adminList(): List<PayoutResponse> = payoutService.adminList()

    @PatchMapping("/api/admin/payouts/{payoutId}")
    fun markPaid(
        @PathVariable payoutId: UUID,
        @RequestBody input: MarkPaidInput,
    ): PayoutResponse = payoutService.markPaid(payoutId, input)
}
