package com.storepilot.backend.store

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class StoreVerificationChangeRequestStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    APPROVED("approved"),
    REJECTED("rejected"),
}

@Converter(autoApply = true)
class StoreVerificationChangeRequestStatusConverter :
    WireValueEnumConverter<StoreVerificationChangeRequestStatus>(StoreVerificationChangeRequestStatus.entries.toTypedArray())
