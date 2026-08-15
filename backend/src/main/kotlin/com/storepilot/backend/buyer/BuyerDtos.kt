package com.storepilot.backend.buyer

import java.time.Instant
import java.util.UUID

/** Shape matches src/types/buyer.ts's Buyer exactly. Saved addresses are fetched separately via GET /api/me/addresses, not embedded here. */
data class BuyerResponse(
    val id: UUID,
    val name: String,
    val email: String,
    val phone: String?,
    val createdAt: Instant,
)
