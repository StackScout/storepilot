package com.storepilot.backend.payout

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class FeeCollectionStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    COLLECTED("collected"),
}

@Converter(autoApply = true)
class FeeCollectionStatusConverter : WireValueEnumConverter<FeeCollectionStatus>(FeeCollectionStatus.entries.toTypedArray())
