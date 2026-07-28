package com.islandcart.backend.payout

import com.islandcart.backend.order.OrderResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Mirrors PayoutController — the reverse-direction ledger, see FeeCollection's doc comment. */
@RestController
class FeeCollectionController(
    private val feeCollectionService: FeeCollectionService,
) {
    @GetMapping("/api/stores/{storeId}/fee-collections")
    fun listByStore(@PathVariable storeId: UUID): List<FeeCollectionResponse> = feeCollectionService.listByStore(storeId)

    @GetMapping("/api/stores/{storeId}/fee-collections/eligible-orders")
    fun eligibleOrders(@PathVariable storeId: UUID): List<OrderResponse> = feeCollectionService.getEligibleOrders(storeId)

    @PostMapping("/api/admin/stores/{storeId}/fee-collections")
    fun createBatch(@PathVariable storeId: UUID): ResponseEntity<FeeCollectionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(feeCollectionService.createBatch(storeId))

    @GetMapping("/api/admin/fee-collections")
    fun adminList(): List<FeeCollectionResponse> = feeCollectionService.adminList()

    @PatchMapping("/api/admin/fee-collections/{feeCollectionId}")
    fun markCollected(
        @PathVariable feeCollectionId: UUID,
        @RequestBody input: MarkCollectedInput,
    ): FeeCollectionResponse = feeCollectionService.markCollected(feeCollectionId, input)
}
