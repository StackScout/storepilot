package com.storepilot.backend.store

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class StoreStaffInviteStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    ACCEPTED("accepted"),
    EXPIRED("expired"),
    REVOKED("revoked"),
}

@Converter(autoApply = true)
class StoreStaffInviteStatusConverter :
    WireValueEnumConverter<StoreStaffInviteStatus>(StoreStaffInviteStatus.entries.toTypedArray())
