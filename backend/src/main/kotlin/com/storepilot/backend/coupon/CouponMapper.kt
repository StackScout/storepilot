package com.storepilot.backend.coupon

fun Coupon.toResponse(): CouponResponse =
    CouponResponse(
        id = requireNotNull(id),
        code = code,
        storeId = store?.id,
        discountType = discountType.wireValue,
        discountValue = discountValue,
        appliesToOrders = appliesToOrders,
        appliesToBookings = appliesToBookings,
        maxUses = maxUses,
        usedCount = usedCount,
        minSubtotal = minSubtotal,
        expiresAt = expiresAt,
        active = active,
        createdAt = requireNotNull(createdAt),
    )
