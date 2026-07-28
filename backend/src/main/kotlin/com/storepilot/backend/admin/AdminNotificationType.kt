package com.storepilot.backend.admin

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** Extensible — currently only fired for payout bank-detail changes, but the shape supports future admin-facing activity types. */
enum class AdminNotificationType(override val wireValue: String) : WireValue {
    BANK_DETAILS_CHANGED("bank-details-changed"),
}

@Converter(autoApply = true)
class AdminNotificationTypeConverter :
    WireValueEnumConverter<AdminNotificationType>(AdminNotificationType.entries.toTypedArray())
