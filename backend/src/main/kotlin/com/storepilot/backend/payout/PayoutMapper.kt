package com.storepilot.backend.payout

fun Payout.toResponse(): PayoutResponse =
    PayoutResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        orders = sourceRefs.map {
            PayoutOrderRefResponse(
                orderId = it.orderId,
                orderNumber = it.orderNumber,
                bookingId = it.bookingId,
                bookingNumber = it.bookingNumber,
                subtotal = it.subtotal,
                platformFee = it.platformFee,
                net = it.net,
            )
        },
        subtotal = subtotal,
        platformFee = platformFee,
        net = net,
        status = status.wireValue,
        createdAt = requireNotNull(createdAt),
        paidAt = paidAt,
        bankReference = bankReference,
    )
