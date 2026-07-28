package com.storepilot.backend.payout

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class PayoutStatus(override val wireValue: String) : WireValue {
    SCHEDULED("scheduled"),
    PAID("paid"),
}

@Converter(autoApply = true)
class PayoutStatusConverter : WireValueEnumConverter<PayoutStatus>(PayoutStatus.entries.toTypedArray())
