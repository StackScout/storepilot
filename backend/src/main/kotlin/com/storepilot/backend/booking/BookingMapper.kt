package com.storepilot.backend.booking

import com.storepilot.backend.order.ReceiptStorageService

/** receiptUrl is resolved fresh on every call (never cached) — see ReceiptStorageService.resolveUrl. */
fun Booking.toResponse(receiptStorageService: ReceiptStorageService): BookingResponse =
    BookingResponse(
        id = requireNotNull(id),
        bookingNumber = bookingNumber,
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        serviceId = requireNotNull(service.id),
        serviceName = serviceName,
        servicePrice = servicePrice,
        serviceDurationMinutes = serviceDurationMinutes,
        scheduledStart = scheduledStart,
        scheduledEnd = scheduledEnd,
        platformFee = platformFee,
        total = total,
        status = status.wireValue,
        paymentMethod = paymentMethod.wireValue,
        paymentStatus = paymentStatus.wireValue,
        receiptUrl = receiptUrl?.let { receiptStorageService.resolveUrl(it) },
        buyerName = buyerName,
        buyerPhone = buyerPhone,
        buyerEmail = buyerEmail,
        buyerId = buyer?.id,
        cancellationReason = cancellationReason,
        timeline = timeline.map {
            BookingTimelineEntryResponse(
                status = it.status.wireValue,
                label = it.label,
                timestamp = it.timestamp,
                note = it.note,
            )
        },
        createdAt = requireNotNull(createdAt),
    )
