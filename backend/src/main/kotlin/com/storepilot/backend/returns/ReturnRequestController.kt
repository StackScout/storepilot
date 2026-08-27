package com.storepilot.backend.returns

import com.storepilot.backend.common.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#returns. */
@RestController
class ReturnRequestController(
    private val returnRequestService: ReturnRequestService,
) {
    @PostMapping("/api/orders/{orderId}/returns")
    fun create(
        @PathVariable orderId: UUID,
        @RequestBody input: ReturnRequestCreateInput,
    ): ResponseEntity<ReturnRequestResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(returnRequestService.create(orderId, input))

    @GetMapping("/api/orders/{orderId}/returns")
    fun listForOrder(@PathVariable orderId: UUID): List<ReturnRequestResponse> = returnRequestService.listForOrder(orderId)

    @PostMapping("/api/orders/{orderId}/returns/{returnId}/decision")
    fun decide(
        @PathVariable orderId: UUID,
        @PathVariable returnId: UUID,
        @RequestBody input: ReturnRequestDecisionInput,
    ): ReturnRequestResponse = returnRequestService.decide(orderId, returnId, input)

    @PostMapping("/api/orders/{orderId}/returns/{returnId}/mark-refunded")
    fun markRefundedBySeller(
        @PathVariable orderId: UUID,
        @PathVariable returnId: UUID,
        @RequestBody input: ReturnRequestMarkRefundedInput,
    ): ReturnRequestResponse = returnRequestService.markRefundedBySeller(orderId, returnId, input)

    @GetMapping("/api/stores/{storeId}/returns")
    fun listForStore(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ReturnRequestResponse> = returnRequestService.listForStore(storeId, page, size)

    // --- Admin — gated by SecurityConfig's hasRole("ADMIN") on /api/admin/** ---

    @GetMapping("/api/admin/returns")
    fun adminList(
        @RequestParam status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<ReturnRequestResponse> = returnRequestService.adminList(status, page, size)

    @PatchMapping("/api/admin/returns/{returnId}")
    fun adminMarkRefunded(
        @PathVariable returnId: UUID,
        @RequestBody input: ReturnRequestMarkRefundedInput,
    ): ReturnRequestResponse = returnRequestService.adminMarkRefunded(returnId, input)
}
