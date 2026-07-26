package com.islandcart.backend.store

import com.islandcart.backend.common.WireValue
import com.islandcart.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** Whether a seller registered as a private individual or a registered business. */
enum class SellerType(override val wireValue: String) : WireValue {
    INDIVIDUAL("individual"),
    BUSINESS("business"),
}

@Converter(autoApply = true)
class SellerTypeConverter : WireValueEnumConverter<SellerType>(SellerType.entries.toTypedArray())
