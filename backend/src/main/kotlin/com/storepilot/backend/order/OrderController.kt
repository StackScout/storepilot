package com.storepilot.backend.order

import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.sse.SseHub
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#orders. */
@RestController
class OrderController(
    private val orderService: OrderService,
    private val sseHub: SseHub,
) {
    @GetMapping("/api/stores/{storeId}/orders")
    fun listByStore(
        @PathVariable storeId: UUID,
        @RequestParam status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.listByStore(storeId, status, page, size)

    @GetMapping("/api/me/orders")
    fun listByCurrentBuyer(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.listByCurrentBuyer(page, size)

    @GetMapping("/api/stores/{storeId}/stripe-settlements")
    fun stripeSettlements(
        @PathVariable storeId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.listStripeSettlementsByStore(storeId, page, size)

    @GetMapping("/api/admin/stripe-settlements")
    fun adminStripeSettlements(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<OrderResponse> = orderService.adminListStripeSettlements(page, size)

    /** First step of guest lookup — see OrderService.requestLookupCode's doc comment. Always 204, regardless of whether orderNumber/phone matched anything. */
    @PostMapping("/api/orders/lookup/request-code")
    fun requestLookupCode(@Valid @RequestBody input: GuestLookupRequestInput): ResponseEntity<Void> {
        orderService.requestLookupCode(input.orderNumber, input.phone)
        return ResponseEntity.noContent().build()
    }

    /** Second step of guest lookup — replaces the old GET /api/orders/lookup?orderNumber=&phone= (phone alone was too weak, see docs/roadmap.md). */
    @PostMapping("/api/orders/lookup/verify")
    fun verifyLookupCode(@Valid @RequestBody input: GuestLookupVerifyInput): OrderResponse =
        orderService.verifyLookupCode(input.orderNumber, input.phone, input.code)

    @GetMapping("/api/orders/{id}")
    fun getById(@PathVariable id: UUID): OrderResponse = orderService.getById(id)

    /** Live order-status push — same "order ID is proof enough" auth model as the GET above. Emits a "status" event with the fresh OrderResponse body each time OrderService writes a change; see OrderService's sseHub.publish call sites. */
    @GetMapping("/api/orders/{id}/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeToEvents(@PathVariable id: UUID): SseEmitter {
        orderService.getById(id) // 404s if the order doesn't exist, same as the GET above
        return sseHub.subscribe("order:$id")
    }

    @PostMapping("/api/orders")
    fun create(@Valid @RequestBody input: CheckoutInput): ResponseEntity<OrderResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(input))

    @PatchMapping("/api/orders/{id}/status", consumes = ["multipart/form-data"])
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestPart("data") input: OrderStatusUpdateInput,
        @RequestPart(value = "courierReceipt", required = false) courierReceipt: MultipartFile?,
    ): OrderResponse = orderService.updateStatus(id, input, courierReceipt)

    @PostMapping("/api/orders/{id}/receipt", consumes = ["multipart/form-data"])
    fun uploadReceipt(
        @PathVariable id: UUID,
        @RequestPart file: MultipartFile,
    ): OrderResponse = orderService.uploadReceipt(id, file)

    @PostMapping("/api/orders/{id}/verify-bank-transfer")
    fun verifyBankTransfer(
        @PathVariable id: UUID,
        @RequestBody input: VerifyBankTransferInput,
    ): OrderResponse = orderService.verifyBankTransfer(id, input)

    @PostMapping("/api/orders/{id}/cancel")
    fun cancel(@PathVariable id: UUID): OrderResponse = orderService.cancelBankTransferOrder(id)
}
