package com.storepilot.backend.admin

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** Extensible — fired for payout bank-detail changes and seller-submitted verification change requests, but the shape supports future admin-facing activity types. */
enum class AdminNotificationType(override val wireValue: String) : WireValue {
    BANK_DETAILS_CHANGED("bank-details-changed"),
    VERIFICATION_CHANGE_REQUESTED("verification-change-requested"),
}

@Converter(autoApply = true)
class AdminNotificationTypeConverter :
    WireValueEnumConverter<AdminNotificationType>(AdminNotificationType.entries.toTypedArray())
