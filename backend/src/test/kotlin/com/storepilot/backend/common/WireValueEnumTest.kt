package com.storepilot.backend.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private enum class Widget(override val wireValue: String) : WireValue {
    SMALL("small"),
    LARGE("large"),
}

private class WidgetConverter : WireValueEnumConverter<Widget>(Widget.entries.toTypedArray())

class WireValueEnumTest {
    private val converter = WidgetConverter()

    @Test
    fun `wireValueOf resolves a matching wire string to its enum constant`() {
        assertEquals(Widget.LARGE, wireValueOf<Widget>("large"))
    }

    @Test
    fun `wireValueOf throws a descriptive error for an unknown wire string`() {
        val ex = assertThrows(IllegalArgumentException::class.java) { wireValueOf<Widget>("medium") }
        assertEquals("Invalid value \"medium\" for Widget; expected one of small, large", ex.message)
    }

    @Test
    fun `converter serializes an enum constant to its wire value`() {
        assertEquals("small", converter.convertToDatabaseColumn(Widget.SMALL))
    }

    @Test
    fun `converter serializes null to null`() {
        assertNull(converter.convertToDatabaseColumn(null))
    }

    @Test
    fun `converter parses a stored wire value back into its enum constant`() {
        assertEquals(Widget.LARGE, converter.convertToEntityAttribute("large"))
    }

    @Test
    fun `converter parses a stored null back into null`() {
        assertNull(converter.convertToEntityAttribute(null))
    }
}
