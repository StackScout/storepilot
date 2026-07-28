package com.storepilot.backend.common

import jakarta.persistence.AttributeConverter

/**
 * Implemented by every domain enum whose JSON/DB representation must match
 * the exact string literals already documented in the frontend's TypeScript
 * union types (under src/types) and docs/api-contracts.md (e.g.
 * "food-beverage", "out-of-stock") — not Kotlin's default enum `.name`
 * (FOOD_BEVERAGE). Keeping the wire format stable is what lets the
 * frontend's mock service layer be pointed at this API without changing its
 * TypeScript types.
 */
interface WireValue {
    val wireValue: String
}

/**
 * Generic JPA converter so each enum only has to declare
 * `class FooConverter : WireValueEnumConverter<Foo>(Foo.entries.toTypedArray())`
 * instead of hand-writing `convertToDatabaseColumn`/`convertToEntityAttribute`
 * every time.
 */
/** Parses a wire string (e.g. "food-beverage") back into its enum constant, for use in service-layer code (not just JPA conversion). Throws IllegalArgumentException — mapped to 400 VALIDATION_ERROR by GlobalExceptionHandler — if the value doesn't match any constant. */
inline fun <reified T> wireValueOf(value: String): T where T : Enum<T>, T : WireValue =
    enumValues<T>().firstOrNull { it.wireValue == value }
        ?: throw IllegalArgumentException(
            "Invalid value \"$value\" for ${T::class.simpleName}; expected one of " +
                enumValues<T>().joinToString(", ") { it.wireValue },
        )

abstract class WireValueEnumConverter<T>(
    private val values: Array<T>,
) : AttributeConverter<T, String> where T : Enum<T>, T : WireValue {
    override fun convertToDatabaseColumn(attribute: T?): String? = attribute?.wireValue

    override fun convertToEntityAttribute(dbData: String?): T? =
        dbData?.let { value -> values.first { it.wireValue == value } }
}
