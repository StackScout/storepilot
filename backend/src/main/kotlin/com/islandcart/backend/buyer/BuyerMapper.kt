package com.islandcart.backend.buyer

import com.islandcart.backend.order.ShippingDetailsResponse

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
                district = it.district,
                postalCode = it.postalCode,
            )
        },
        createdAt = requireNotNull(createdAt),
    )
