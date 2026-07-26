package com.islandcart.backend.order

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#orders. */
@RestController
class OrderController(
    private val orderService: OrderService,
) {
    @GetMapping("/api/stores/{storeId}/orders")
    fun listByStore(
        @PathVariable storeId: UUID,
        @RequestParam status: String?,
    ): List<OrderResponse> = orderService.listByStore(storeId, status)

    @GetMapping("/api/buyers/{buyerId}/orders")
    fun listByBuyer(@PathVariable buyerId: UUID): List<OrderResponse> = orderService.listByBuyer(buyerId)

    @GetMapping("/api/orders/lookup")
    fun lookup(
        @RequestParam orderNumber: String,
        @RequestParam phone: String,
    ): ResponseEntity<OrderResponse> {
        val order = orderService.findByNumberAndPhone(orderNumber, phone) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(order)
    }

    @GetMapping("/api/orders/{id}")
    fun getById(@PathVariable id: UUID): OrderResponse = orderService.getById(id)

    @PostMapping("/api/orders")
    fun create(@Valid @RequestBody input: CheckoutInput): ResponseEntity<OrderResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(input))

    @PatchMapping("/api/orders/{id}/status")
    fun updateStatus(
        @PathVariable id: UUID,
        @Valid @RequestBody input: OrderStatusUpdateInput,
    ): OrderResponse = orderService.updateStatus(id, input)

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
