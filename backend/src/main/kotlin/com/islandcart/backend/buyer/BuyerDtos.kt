package com.islandcart.backend.buyer

import com.islandcart.backend.order.ShippingDetailsInput
import com.islandcart.backend.order.ShippingDetailsResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** Shape matches src/types/buyer.ts's Buyer exactly. */
data class BuyerResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String?,
    val defaultShipping: ShippingDetailsResponse?,
    val createdAt: Instant,
)

/** Mirrors src/types/buyer.ts's BuyerRegistrationInput — POST /api/buyers. */
data class BuyerRegistrationInput(
    @field:NotBlank(message = "Enter your full name")
    val name: String,
    @field:Email(message = "Enter a valid email")
    @field:NotBlank(message = "Enter a valid email")
    val email: String,
    val phone: String? = null,
)
