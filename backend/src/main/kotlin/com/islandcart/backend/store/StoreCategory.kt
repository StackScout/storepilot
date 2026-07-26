package com.islandcart.backend.store

import com.islandcart.backend.common.WireValue
import com.islandcart.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/** Mirrors src/types/store.ts's StoreCategory union exactly. */
enum class StoreCategory(override val wireValue: String) : WireValue {
    FASHION("fashion"),
    FOOD_BEVERAGE("food-beverage"),
    BEAUTY("beauty"),
    HANDICRAFTS("handicrafts"),
    ELECTRONICS("electronics"),
    HOME_LIVING("home-living"),
    JEWELRY("jewelry"),
    GROCERY("grocery"),
}

@Converter(autoApply = true)
class StoreCategoryConverter : WireValueEnumConverter<StoreCategory>(StoreCategory.entries.toTypedArray())
