package com.storepilot.backend.product

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class ProductStatus(override val wireValue: String) : WireValue {
    ACTIVE("active"),
    DRAFT("draft"),
    OUT_OF_STOCK("out-of-stock"),
}

@Converter(autoApply = true)
class ProductStatusConverter : WireValueEnumConverter<ProductStatus>(ProductStatus.entries.toTypedArray())
