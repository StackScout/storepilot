package com.storepilot.backend.booking

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant
import java.util.UUID

data class BookableServiceImageResponse(
    val id: UUID,
    val url: String,
    val alt: String,
)

/** Shape matches src/types/booking.ts's BookableService exactly. */
data class BookableServiceResponse(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val storeSlug: String,
    val name: String,
    val slug: String,
    val description: String,
    val images: List<BookableServiceImageResponse>,
    val category: String,
    val price: Int,
    val durationMinutes: Int,
    val bufferMinutes: Int,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Mirrors ProductFormInput's shape — used for both create and update, the
 * frontend's ServiceForm always resubmits the full shape rather than a
 * partial patch.
 */
data class BookableServiceFormInput(
    @field:NotBlank(message = "Enter a service name")
    val name: String,
    @field:NotBlank(message = "Enter a description")
    val description: String,
    @field:NotBlank(message = "Select a category")
    val category: String,
    @field:Min(value = 1, message = "Price must be positive")
    val price: Int,
    @field:Min(value = 1, message = "Duration must be at least 1 minute")
    val durationMinutes: Int,
    @field:PositiveOrZero(message = "Buffer must be zero or more")
    val bufferMinutes: Int = 0,
    @field:NotBlank(message = "Select a status")
    val status: String,
)
