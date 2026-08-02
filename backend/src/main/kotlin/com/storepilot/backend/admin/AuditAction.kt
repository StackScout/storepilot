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
}

@Converter(autoApply = true)
class AuditActionConverter : WireValueEnumConverter<AuditAction>(AuditAction.entries.toTypedArray())
