package com.storepilot.backend.returns

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** Shape matches src/types/return.ts's ReturnRequest exactly. */
data class ReturnRequestResponse(
    val id: UUID,
    val orderId: UUID,
    val orderNumber: String,
    val storeId: UUID,
    val storeName: String,
    val paymentMethod: String,
    val reasonCategory: String,
    val reasonNote: String?,
    val status: String,
    val sellerDecisionNote: String?,
    val refundReference: String?,
    val settlementReconciliationNote: String?,
    val createdAt: Instant,
    val decidedAt: Instant?,
    val refundedAt: Instant?,
)

/** POST /api/orders/{orderId}/returns — buyer request. */
data class ReturnRequestCreateInput(
    @field:NotBlank(message = "Select a reason")
    val reasonCategory: String,
    val reasonNote: String? = null,
)

/** POST /api/orders/{orderId}/returns/{returnId}/decision — seller approve/reject. */
data class ReturnRequestDecisionInput(
    val approved: Boolean,
    val note: String? = null,
)

/** POST .../mark-refunded (seller, COD/bank-transfer) and PATCH /api/admin/returns/{id} (admin, PayHere) — both confirm the same thing (money actually moved), just gated to a different actor depending on who held the money. */
data class ReturnRequestMarkRefundedInput(
    val refundReference: String? = null,
)
