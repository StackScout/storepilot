package com.storepilot.backend.coupon

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import com.storepilot.backend.store.Store
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * A discount code, either store-specific (`store` set, created/managed by
 * that store's seller) or platform-wide (`store` null, admin-managed). Both
 * kinds are validated/applied through the same CouponService.resolve() path
 * — see its doc comment for the full rule set. `usedCount` increments once
 * per checkout (not per line item, and not per occurrence for a recurring
 * booking series — see BookingService.createBooking) via
 * CouponService.recordUse, called from inside the same transaction as the
 * order/booking write it discounted.
 */
@Entity
@Table(name = "coupons")
class Coupon(
    @Column(nullable = false, unique = true)
    var code: String,
    /** Null = platform-wide (admin-managed); set = store-specific (seller-managed, only valid for checkouts against that store). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    var store: Store? = null,
    @Column(name = "discount_type", nullable = false)
    var discountType: DiscountType,
    /** Percent (1-100) when discountType is PERCENT; cents when FIXED. */
    @Column(name = "discount_value", nullable = false)
    var discountValue: Int,
    @Column(name = "applies_to_orders", nullable = false)
    var appliesToOrders: Boolean = true,
    @Column(name = "applies_to_bookings", nullable = false)
    var appliesToBookings: Boolean = true,
    /** Null = unlimited uses. */
    @Column(name = "max_uses")
    var maxUses: Int? = null,
    @Column(name = "used_count", nullable = false)
    var usedCount: Int = 0,
    /** Cents — the order subtotal or booking service price must be at least this for the coupon to apply. */
    @Column(name = "min_subtotal", nullable = false)
    var minSubtotal: Int = 0,
    @Column(name = "expires_at")
    var expiresAt: Instant? = null,
    @Column(nullable = false)
    var active: Boolean = true,
) : BaseEntity()

enum class DiscountType(override val wireValue: String) : WireValue {
    PERCENT("percent"),
    FIXED("fixed"),
}

@Converter(autoApply = true)
class DiscountTypeConverter : WireValueEnumConverter<DiscountType>(DiscountType.entries.toTypedArray())

/** What kind of checkout a coupon is being resolved against — see CouponService.resolve. */
enum class CouponKind(override val wireValue: String) : WireValue {
    ORDER("order"),
    BOOKING("booking"),
}
