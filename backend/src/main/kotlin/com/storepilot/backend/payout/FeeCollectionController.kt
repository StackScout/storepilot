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

/** Mirrors PayoutController — the reverse-direction ledger, see FeeCollection's doc comment. */
@RestController
class FeeCollectionController(
    private val feeCollectionService: FeeCollectionService,
) {
    @GetMapping("/api/stores/{storeId}/fee-collections")
    fun listByStore(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<FeeCollectionResponse> = feeCollectionService.listByStore(storeId, page, size)

    @GetMapping("/api/stores/{storeId}/fee-collections/eligible-orders")
    fun eligibleOrders(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = feeCollectionService.getEligibleOrders(storeId, page, size)

    @GetMapping("/api/stores/{storeId}/fee-collections/eligible-bookings")
    fun eligibleBookings(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<BookingResponse> = feeCollectionService.getEligibleBookings(storeId, page, size)

    /** Admin-only — see FeeCollectionService.adminGetEligibleOrders's doc comment. */
    @GetMapping("/api/admin/stores/{storeId}/fee-collections/eligible-orders")
    fun adminEligibleOrders(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = feeCollectionService.adminGetEligibleOrders(storeId, page, size)

    /** Admin-only — see FeeCollectionService.adminGetEligibleBookings's doc comment. */
    @GetMapping("/api/admin/stores/{storeId}/fee-collections/eligible-bookings")
    fun adminEligibleBookings(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<BookingResponse> = feeCollectionService.adminGetEligibleBookings(storeId, page, size)

    @PostMapping("/api/admin/stores/{storeId}/fee-collections")
    fun createBatch(@PathVariable storeId: UUID): ResponseEntity<FeeCollectionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(feeCollectionService.createBatch(storeId))

    @GetMapping("/api/admin/fee-collections")
    fun adminList(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<FeeCollectionResponse> = feeCollectionService.adminList(page, size)

    @PatchMapping("/api/admin/fee-collections/{feeCollectionId}")
    fun markCollected(
        @PathVariable feeCollectionId: UUID,
        @RequestBody input: MarkCollectedInput,
    ): FeeCollectionResponse = feeCollectionService.markCollected(feeCollectionId, input)
}
