package com.storepilot.backend.review

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ReviewInput(
    @field:Min(value = 1, message = "Rating must be at least 1")
    @field:Max(value = 5, message = "Rating must be at most 5")
    val rating: Int,
    @field:Size(max = 2000, message = "Review is too long")
    val comment: String? = null,
)

data class ReviewResponse(
    val id: UUID,
    val buyerName: String,
    val rating: Int,
    val comment: String?,
    val productId: UUID?,
    val createdAt: Instant,
)
