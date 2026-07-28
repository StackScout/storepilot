package com.storepilot.backend.buyer

import com.storepilot.backend.order.ShippingDetailsResponse

fun Buyer.toResponse(): BuyerResponse =
    BuyerResponse(
        id = requireNotNull(id),
        name = name,
        email = email,
        phone = phone,
        defaultShipping = defaultShipping?.let {
            ShippingDetailsResponse(
                fullName = it.fullName,
                phone = it.phone,
                addressLine1 = it.addressLine1,
                city = it.city,
                state = it.state,
                postalCode = it.postalCode,
            )
        },
        createdAt = requireNotNull(createdAt),
    )
