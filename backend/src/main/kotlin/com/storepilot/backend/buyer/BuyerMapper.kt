package com.storepilot.backend.buyer

fun Buyer.toResponse(): BuyerResponse =
    BuyerResponse(
        id = requireNotNull(id),
        name = name,
        email = email,
        phone = phone,
        createdAt = requireNotNull(createdAt),
    )
