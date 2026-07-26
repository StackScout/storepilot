package com.islandcart.backend.order

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.Instant
import java.util.UUID

data class OrderItemResponse(
    val productId: UUID,
    val productName: String,
    val productImageUrl: String,
    val unitPriceLkr: Int,
    val quantity: Int,
)

data class OrderTimelineEntryResponse(
    val status: String,
    val label: String,
    val timestamp: Instant,
    val note: String?,
)

data class ShippingDetailsResponse(
    val fullName: String?,
    val phone: String?,
    val addressLine1: String?,
    val city: String?,
    val district: String?,
    val postalCode: String?,
)

/** Shape matches src/types/order.ts's Order exactly. */
data class OrderResponse(
    val id: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val storeName: String,
    val storeSlug: String,
    val items: List<OrderItemResponse>,
    val subtotalLkr: Int,
    val shippingFeeLkr: Int,
    val platformFeeLkr: Int,
    val totalLkr: Int,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val receiptUrl: String?,
    val shipping: ShippingDetailsResponse,
    val timeline: List<OrderTimelineEntryResponse>,
    val createdAt: Instant,
    val buyerEmail: String,
    val buyerId: UUID?,
)

data class ShippingDetailsInput(
    @field:NotBlank(message = "Enter the recipient's full name")
    val fullName: String,
    @field:NotBlank(message = "Enter a valid phone number")
    val phone: String,
    @field:NotBlank(message = "Enter the delivery address")
    val addressLine1: String,
    @field:NotBlank(message = "Enter a city/town")
    val city: String,
    @field:NotBlank(message = "Select a district")
    val district: String,
    @field:NotBlank(message = "Enter a postal code")
    val postalCode: String,
)

data class CheckoutItemInput(
    val productId: UUID,
    val quantity: Int,
)

/** Mirrors src/types/order.ts's CheckoutInput — POST /api/orders. */
data class CheckoutInput(
    val storeId: UUID,
    @field:NotEmpty(message = "Cart is empty")
    val items: List<CheckoutItemInput>,
    @field:Valid
    val shipping: ShippingDetailsInput,
    @field:NotBlank(message = "Select a payment method")
    val paymentMethod: String,
    @field:Email(message = "Enter a valid email")
    @field:NotBlank(message = "Enter a valid email")
    val email: String,
    val buyerId: UUID? = null,
)

data class OrderStatusUpdateInput(
    @field:NotBlank(message = "Status is required")
    val status: String,
    val note: String? = null,
)

/** POST /api/orders/{id}/verify-bank-transfer — seller accepts or rejects the buyer's uploaded receipt. */
data class VerifyBankTransferInput(
    val approved: Boolean,
    val note: String? = null,
)
