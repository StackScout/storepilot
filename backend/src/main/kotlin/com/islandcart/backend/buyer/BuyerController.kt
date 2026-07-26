package com.islandcart.backend.buyer

import com.islandcart.backend.order.ShippingDetailsInput
import jakarta.validation.Valid
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

/** Matches the endpoints documented in docs/api-contracts.md#buyer-accounts. */
@RestController
class BuyerController(
    private val buyerService: BuyerService,
) {
    @PostMapping("/api/buyers")
    fun register(@Valid @RequestBody input: BuyerRegistrationInput): ResponseEntity<BuyerResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(buyerService.register(input))

    @GetMapping("/api/buyers/by-email")
    fun getByEmail(@RequestParam email: String): ResponseEntity<BuyerResponse> {
        val buyer = buyerService.getByEmail(email) ?: return ResponseEntity.ok(null)
        return ResponseEntity.ok(buyer)
    }

    @GetMapping("/api/buyers/{id}")
    fun getById(@PathVariable id: UUID): BuyerResponse = buyerService.getById(id)

    @PatchMapping("/api/buyers/{id}/default-shipping")
    fun updateDefaultShipping(
        @PathVariable id: UUID,
        @Valid @RequestBody input: ShippingDetailsInput,
    ): BuyerResponse = buyerService.updateDefaultShipping(id, input)
}
