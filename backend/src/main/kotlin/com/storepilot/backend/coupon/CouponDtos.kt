package com.storepilot.backend.coupon

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** Shape matches src/types/coupon.ts's Coupon exactly. */
data class CouponResponse(
    val id: UUID,
    val code: String,
    val storeId: UUID?,
    val discountType: String,
    val discountValue: Int,
    val appliesToOrders: Boolean,
    val appliesToBookings: Boolean,
    val maxUses: Int?,
    val usedCount: Int,
    val minSubtotal: Int,
    val expiresAt: Instant?,
    val active: Boolean,
    val createdAt: Instant,
)

/** POST/PATCH body for both seller- and admin-scoped coupon management — storeId is never in the body, it's derived from the path (seller) or omitted entirely (admin, meaning platform-wide). */
data class CouponInput(
    @field:NotBlank(message = "Enter a coupon code")
    val code: String,
    @field:NotBlank(message = "Select a discount type")
    val discountType: String,
    @field:Min(1, message = "Discount value must be at least 1")
    val discountValue: Int,
    val appliesToOrders: Boolean = true,
    val appliesToBookings: Boolean = true,
    val maxUses: Int? = null,
    val minSubtotal: Int = 0,
    val expiresAt: Instant? = null,
    val active: Boolean = true,
)

/**
 * POST /api/coupons/preview — public, side-effect-free dry run of
 * CouponService.resolve so the checkout/booking form can show the discount
 * before the buyer actually submits. [amount] is the order subtotal or
 * booking service price in cents, computed client-side from data the client
 * already has (cart contents / selected service price) — never trusted for
 * the real discount, which OrderService/BookingService recompute themselves
 * server-side from the authoritative product/service prices.
 */
data class CouponPreviewInput(
    @field:NotBlank(message = "Enter a coupon code")
    val code: String,
    val storeId: UUID,
    @field:NotBlank(message = "kind is required")
    val kind: String,
    @field:Min(0, message = "amount must be non-negative")
    @field:Max(Int.MAX_VALUE.toLong(), message = "amount too large")
    val amount: Int,
)

data class CouponPreviewResponse(
    val valid: Boolean,
    val discountAmount: Int,
    val message: String?,
)
