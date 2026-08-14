package com.storepilot.backend.payout

fun FeeCollection.toResponse(): FeeCollectionResponse =
    FeeCollectionResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        orders = sourceRefs.map {
            FeeCollectionOrderRefResponse(
                orderId = it.orderId,
                orderNumber = it.orderNumber,
                bookingId = it.bookingId,
                bookingNumber = it.bookingNumber,
                subtotal = it.subtotal,
                platformFee = it.platformFee,
            )
        },
        subtotal = subtotal,
        platformFee = platformFee,
        status = status.wireValue,
        createdAt = requireNotNull(createdAt),
        collectedAt = collectedAt,
        reference = reference,
    )
