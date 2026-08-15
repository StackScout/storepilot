package com.storepilot.backend.buyer

import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The authenticated buyer's own address book — falls under the existing /api/me prefix's ROLE_BUYER gate in SecurityConfig, no new matcher needed. */
@RestController
class AddressController(
    private val addressService: AddressService,
) {
    @GetMapping("/api/me/addresses")
    fun list(): List<AddressResponse> = addressService.list()

    @PostMapping("/api/me/addresses")
    fun create(@Valid @RequestBody input: AddressInput): AddressResponse = addressService.create(input)

    @PatchMapping("/api/me/addresses/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody input: AddressInput): AddressResponse =
        addressService.update(id, input)

    @PostMapping("/api/me/addresses/{id}/default")
    fun setDefault(@PathVariable id: UUID): AddressResponse = addressService.setDefault(id)

    @DeleteMapping("/api/me/addresses/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        addressService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
