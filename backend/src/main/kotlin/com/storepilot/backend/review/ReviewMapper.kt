package com.storepilot.backend.review

fun Review.toResponse(): ReviewResponse =
    ReviewResponse(
        id = requireNotNull(id),
        buyerName = buyer.name,
        rating = rating,
        comment = comment,
        productId = productId,
        createdAt = requireNotNull(createdAt),
    )
