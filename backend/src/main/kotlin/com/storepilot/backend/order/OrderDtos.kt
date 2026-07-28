package com.storepilot.backend.order

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
    val unitPrice: Int,
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
    val state: String?,
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
    val subtotal: Int,
    val shippingFee: Int,
    val platformFee: Int,
    val total: Int,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val receiptUrl: String?,
    val trackingNumber: String?,
    val courierServiceName: String?,
    val courierReceiptUrl: String?,
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
    @field:NotBlank(message = "Select a state/province")
    val state: String,
    @field:NotBlank(message = "Enter a postal code")
    val postalCode: String,
)

data class CheckoutItemInput(
    val productId: UUID,
    val quantity: Int,
)

/**
 * Mirrors src/types/order.ts's CheckoutInput — POST /api/orders. No buyerId
 * field: it was previously client-supplied and completely unverified,
 * letting any caller link an order to an arbitrary buyer's history. The
 * order's buyer link (if any) is derived server-side only, from
 * CurrentActor.buyerOrNull() — null for a guest checkout.
 */
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
)

/** trackingNumber/courierServiceName are required by OrderService.updateStatus specifically when status is "shipped" — not enforced here since that's conditional on the target status, not always mandatory. */
data class OrderStatusUpdateInput(
    @field:NotBlank(message = "Status is required")
    val status: String,
    val note: String? = null,
    val trackingNumber: String? = null,
    val courierServiceName: String? = null,
)

/** POST /api/orders/{id}/verify-bank-transfer — seller accepts or rejects the buyer's uploaded receipt. */
data class VerifyBankTransferInput(
    val approved: Boolean,
    val note: String? = null,
)
