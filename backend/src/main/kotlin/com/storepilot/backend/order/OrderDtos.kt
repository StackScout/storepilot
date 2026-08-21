package com.storepilot.backend.order

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
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
    val deliveryMethod: String,
    val shippingFee: Int,
    val platformFee: Int,
    val total: Int,
    val couponCode: String?,
    val discountAmount: Int,
    /** Both present only when the seller was GST-registered at the time of this order — see Order.kt's doc comment. Their presence is what the frontend uses to render this as a tax invoice rather than a plain confirmation. */
    val sellerAbn: String?,
    val gstAmount: Int?,
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

/**
 * Address fields are nullable (unlike fullName/phone) because a pickup
 * checkout omits them entirely — Jackson's Kotlin module rejects a missing
 * JSON property against a non-nullable constructor param regardless of
 * Bean Validation, so this has to be a real type-level nullability, not
 * just a skipped `@Valid`. `@NotBlank` still fires on null, so
 * BuyerController.updateDefaultShipping (which does apply `@Valid` here —
 * that's a saved address book entry, always meant to be complete) keeps
 * requiring them. CheckoutInput.shipping deliberately skips `@Valid`
 * instead: OrderService.createOrder enforces fullName/phone
 * unconditionally plus the address fields only when deliveryMethod is
 * "shipping" — the same conditional-requiredness pattern
 * OrderStatusUpdateInput's trackingNumber/courierServiceName already use
 * for the SHIPPED-only case.
 */
data class ShippingDetailsInput(
    @field:NotBlank(message = "Enter the recipient's full name")
    val fullName: String,
    @field:NotBlank(message = "Enter a valid phone number")
    val phone: String,
    @field:NotBlank(message = "Enter the delivery address")
    val addressLine1: String? = null,
    @field:NotBlank(message = "Enter a city/town")
    val city: String? = null,
    @field:NotBlank(message = "Select a state/province")
    val state: String? = null,
    @field:NotBlank(message = "Enter a postal code")
    val postalCode: String? = null,
)

data class CheckoutItemInput(
    val productId: UUID,
    @field:Positive(message = "Quantity must be at least 1")
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
    @field:Valid
    val items: List<CheckoutItemInput>,
    // Deliberately not @Valid — see ShippingDetailsInput's doc comment.
    // OrderService.createOrder enforces which fields are required, based
    // on deliveryMethod below.
    val shipping: ShippingDetailsInput,
    @field:NotBlank(message = "Select a payment method")
    val paymentMethod: String,
    @field:NotBlank(message = "Select a delivery method")
    val deliveryMethod: String,
    @field:Email(message = "Enter a valid email")
    @field:NotBlank(message = "Enter a valid email")
    val email: String,
    val couponCode: String? = null,
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

/** POST /api/orders/lookup/request-code — see OrderService.requestLookupCode's doc comment. */
data class GuestLookupRequestInput(
    @field:NotBlank(message = "Order number is required")
    val orderNumber: String,
    @field:NotBlank(message = "Phone number is required")
    val phone: String,
)

/** POST /api/orders/lookup/verify — completes a guest lookup started with GuestLookupRequestInput. */
data class GuestLookupVerifyInput(
    @field:NotBlank(message = "Order number is required")
    val orderNumber: String,
    @field:NotBlank(message = "Phone number is required")
    val phone: String,
    @field:NotBlank(message = "Code is required")
    val code: String,
)
