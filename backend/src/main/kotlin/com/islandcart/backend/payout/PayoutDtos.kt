package com.islandcart.backend.payout

import java.time.Instant
import java.util.UUID

data class PayoutOrderRefResponse(
    val orderId: UUID,
    val orderNumber: String,
    val subtotalLkr: Int,
    val platformFeeLkr: Int,
    val netLkr: Int,
)

/** Shape matches src/types/payout.ts's Payout exactly. */
data class PayoutResponse(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val orders: List<PayoutOrderRefResponse>,
    val subtotalLkr: Int,
    val platformFeeLkr: Int,
    val netLkr: Int,
    val status: String,
    val createdAt: Instant,
    val paidAt: Instant?,
    val bankReference: String?,
)

data class MarkPaidInput(
    val bankReference: String? = null,
)
