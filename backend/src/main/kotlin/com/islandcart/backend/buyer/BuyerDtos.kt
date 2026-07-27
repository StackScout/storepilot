package com.islandcart.backend.buyer

import com.islandcart.backend.order.ShippingDetailsResponse
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
