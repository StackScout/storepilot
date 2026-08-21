package com.storepilot.backend.admin

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** Every distinct action recorded in AuditLog — add a new constant here whenever a new admin action needs an audit trail. */
enum class AuditAction(override val wireValue: String) : WireValue {
    STORE_APPROVED("store_approved"),
    STORE_REJECTED("store_rejected"),
    ADMIN_INVITED("admin_invited"),
    PAYOUT_MARKED_PAID("payout_marked_paid"),
    FEE_COLLECTION_MARKED_COLLECTED("fee_collection_marked_collected"),
    STORE_SETTINGS_UPDATED("store_settings_updated"),
    STORE_VERIFICATION_CHANGE_REQUESTED("store_verification_change_requested"),
    STORE_VERIFICATION_CHANGE_APPROVED("store_verification_change_approved"),
    STORE_VERIFICATION_CHANGE_REJECTED("store_verification_change_rejected"),
    RETURN_REFUND_MARKED_COMPLETE("return_refund_marked_complete"),
    STORE_CLOSED("store_closed"),
    SELLER_ACCOUNT_DELETED("seller_account_deleted"),
    BUYER_ACCOUNT_DELETED("buyer_account_deleted"),
}

@Converter(autoApply = true)
class AuditActionConverter : WireValueEnumConverter<AuditAction>(AuditAction.entries.toTypedArray())
