package com.islandcart.backend.order

/** receiptUrl is resolved fresh on every call (never cached) — see ReceiptStorageService.resolveUrl. */
fun Order.toResponse(receiptStorageService: ReceiptStorageService): OrderResponse =
    OrderResponse(
        id = requireNotNull(id),
        orderNumber = orderNumber,
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        items = items.map {
            OrderItemResponse(
                productId = it.productId,
                productName = it.productName,
                productImageUrl = it.productImageUrl,
                unitPriceLkr = it.unitPriceLkr,
                quantity = it.quantity,
            )
        },
        subtotalLkr = subtotalLkr,
        shippingFeeLkr = shippingFeeLkr,
        platformFeeLkr = platformFeeLkr,
        totalLkr = totalLkr,
        status = status.wireValue,
        paymentMethod = paymentMethod.wireValue,
        paymentStatus = paymentStatus.wireValue,
        receiptUrl = receiptUrl?.let { receiptStorageService.resolveUrl(it) },
        shipping = ShippingDetailsResponse(
            fullName = shipping.fullName,
            phone = shipping.phone,
            addressLine1 = shipping.addressLine1,
            city = shipping.city,
            district = shipping.district,
            postalCode = shipping.postalCode,
        ),
        timeline = timeline.map {
            OrderTimelineEntryResponse(
                status = it.status.wireValue,
                label = it.label,
                timestamp = it.timestamp,
                note = it.note,
            )
        },
        createdAt = requireNotNull(createdAt),
        buyerEmail = buyerEmail,
        buyerId = buyer?.id,
    )
