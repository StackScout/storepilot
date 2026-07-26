package com.islandcart.backend.payout

fun Payout.toResponse(): PayoutResponse =
    PayoutResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        orders = orders.map {
            PayoutOrderRefResponse(
                orderId = it.orderId,
                orderNumber = it.orderNumber,
                subtotalLkr = it.subtotalLkr,
                platformFeeLkr = it.platformFeeLkr,
                netLkr = it.netLkr,
            )
        },
        subtotalLkr = subtotalLkr,
        platformFeeLkr = platformFeeLkr,
        netLkr = netLkr,
        status = status.wireValue,
        createdAt = requireNotNull(createdAt),
        paidAt = paidAt,
        bankReference = bankReference,
    )
