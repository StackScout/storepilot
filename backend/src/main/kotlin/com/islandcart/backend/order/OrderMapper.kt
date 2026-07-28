package com.islandcart.backend.order

import com.islandcart.backend.common.storage.FileStorageService

/** receiptUrl/courierReceiptUrl are resolved fresh on every call (never cached) — see ReceiptStorageService/FileStorageService.resolveUrl. */
fun Order.toResponse(receiptStorageService: ReceiptStorageService, fileStorageService: FileStorageService): OrderResponse =
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
                unitPrice = it.unitPrice,
                quantity = it.quantity,
            )
        },
        subtotal = subtotal,
        shippingFee = shippingFee,
        platformFee = platformFee,
        total = total,
        status = status.wireValue,
        paymentMethod = paymentMethod.wireValue,
        paymentStatus = paymentStatus.wireValue,
        receiptUrl = receiptUrl?.let { receiptStorageService.resolveUrl(it) },
        trackingNumber = trackingNumber,
        courierServiceName = courierServiceName,
        courierReceiptUrl = courierReceiptUrl?.let { fileStorageService.resolveUrl(it) },
        shipping = ShippingDetailsResponse(
            fullName = shipping.fullName,
            phone = shipping.phone,
            addressLine1 = shipping.addressLine1,
            city = shipping.city,
            state = shipping.state,
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
