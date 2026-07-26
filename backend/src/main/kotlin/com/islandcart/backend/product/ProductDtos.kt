package com.islandcart.backend.product

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import java.time.Instant
import java.util.UUID

data class ProductImageResponse(
    val id: UUID,
    val url: String,
    val alt: String,
)

/** Shape matches src/types/product.ts's Product exactly — see api-contracts.md#products. */
data class ProductResponse(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val storeSlug: String,
    val name: String,
    val slug: String,
    val description: String,
    val images: List<ProductImageResponse>,
    val category: String,
    val priceLkr: Int,
    val compareAtPriceLkr: Int?,
    val stockQuantity: Int,
    val status: String,
    val sku: String,
    val rating: Double,
    val reviewCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Mirrors src/types/product.ts's ProductFormInput, used for both create and
 * update — matches how the frontend's ProductForm always resubmits the full
 * shape rather than a partial patch (see docs/features/product-management.md).
 */
data class ProductFormInput(
    @field:NotBlank(message = "Enter a product name")
    val name: String,
    @field:NotBlank(message = "Enter a description")
    val description: String,
    @field:NotBlank(message = "Select a category")
    val category: String,
    @field:Min(value = 1, message = "Price must be positive")
    val priceLkr: Int,
    @field:PositiveOrZero(message = "Compare-at price must be positive")
    val compareAtPriceLkr: Int?,
    @field:PositiveOrZero(message = "Stock quantity must be zero or more")
    val stockQuantity: Int,
    @field:NotBlank(message = "Enter a SKU")
    val sku: String,
    @field:NotBlank(message = "Select a status")
    val status: String,
    @field:NotBlank(message = "Provide an image URL")
    val imageUrl: String,
)
