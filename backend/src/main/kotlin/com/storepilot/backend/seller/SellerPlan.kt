package com.storepilot.backend.seller

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** COD and bank-transfer are Pro-only today (see OrderService.createOrder/StoreService.upsertSettings) — more features are expected to gate on this later. */
enum class SellerPlan(override val wireValue: String) : WireValue {
    FREE("free"),
    PRO("pro"),
}

@Converter(autoApply = true)
class SellerPlanConverter : WireValueEnumConverter<SellerPlan>(SellerPlan.entries.toTypedArray())
