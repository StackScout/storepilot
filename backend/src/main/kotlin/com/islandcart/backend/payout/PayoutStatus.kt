package com.islandcart.backend.payout

import com.islandcart.backend.common.WireValue
import com.islandcart.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class PayoutStatus(override val wireValue: String) : WireValue {
    SCHEDULED("scheduled"),
    PAID("paid"),
}

@Converter(autoApply = true)
class PayoutStatusConverter : WireValueEnumConverter<PayoutStatus>(PayoutStatus.entries.toTypedArray())
