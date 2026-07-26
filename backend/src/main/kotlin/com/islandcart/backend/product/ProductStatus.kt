package com.islandcart.backend.product

import com.islandcart.backend.common.WireValue
import com.islandcart.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

enum class ProductStatus(override val wireValue: String) : WireValue {
    ACTIVE("active"),
    DRAFT("draft"),
    OUT_OF_STOCK("out-of-stock"),
}

@Converter(autoApply = true)
class ProductStatusConverter : WireValueEnumConverter<ProductStatus>(ProductStatus.entries.toTypedArray())
