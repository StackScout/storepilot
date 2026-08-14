package com.storepilot.backend.payout

import java.time.Instant
import java.util.UUID

data class FeeCollectionOrderRefResponse(
    val orderId: UUID?,
    val orderNumber: String?,
    val bookingId: UUID?,
    val bookingNumber: String?,
    val subtotal: Int,
    val platformFee: Int,
)

/** Shape matches src/types/payout.ts's FeeCollection exactly. */
data class FeeCollectionResponse(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val orders: List<FeeCollectionOrderRefResponse>,
    val subtotal: Int,
    val platformFee: Int,
    val status: String,
    val createdAt: Instant,
    val collectedAt: Instant?,
    val reference: String?,
)

data class MarkCollectedInput(
    val reference: String? = null,
)
