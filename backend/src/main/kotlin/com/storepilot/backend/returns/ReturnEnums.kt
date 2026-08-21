package com.storepilot.backend.returns

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class ReturnReasonCategory(override val wireValue: String) : WireValue {
    DEFECTIVE("defective"),
    WRONG_ITEM("wrong-item"),
    NOT_AS_DESCRIBED("not-as-described"),
    CHANGED_MIND("changed-mind"),
    OTHER("other"),
}

@Converter(autoApply = true)
class ReturnReasonCategoryConverter : WireValueEnumConverter<ReturnReasonCategory>(ReturnReasonCategory.entries.toTypedArray())

/**
 * REQUESTED -> APPROVED|REJECTED (seller decision, see
 * ReturnRequestService.decide). APPROVED then either jumps straight to
 * REFUNDED (Stripe — refund fires synchronously in the same call) or moves
 * to REFUND_PENDING (PayHere/COD/bank-transfer — no live refund API exists
 * for any of these, a human still has to move the money; who confirms it
 * is split by payment-method custody, see markRefundedBySeller vs
 * adminMarkRefunded). REJECTED is the only status a new request may be
 * submitted after — see ReturnRequestRepository.existsByOrder_IdAndStatusNot.
 */
enum class ReturnRequestStatus(override val wireValue: String) : WireValue {
    REQUESTED("requested"),
    APPROVED("approved"),
    REJECTED("rejected"),
    REFUND_PENDING("refund-pending"),
    REFUNDED("refunded"),
}

@Converter(autoApply = true)
class ReturnRequestStatusConverter : WireValueEnumConverter<ReturnRequestStatus>(ReturnRequestStatus.entries.toTypedArray())
