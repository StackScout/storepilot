package com.storepilot.backend.abn

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AbnChecksumTest {
    @Test
    fun `accepts a known-valid ABN`() {
        assertTrue(isValidAbnChecksum("51824753556"))
        assertTrue(isValidAbnChecksum("53004085616"))
    }

    @Test
    fun `accepts a valid ABN formatted with spaces`() {
        assertTrue(isValidAbnChecksum("51 824 753 556"))
    }

    @Test
    fun `rejects an ABN with a bad check digit`() {
        assertFalse(isValidAbnChecksum("12345678901"))
        assertFalse(isValidAbnChecksum("11111111111"))
    }

    @Test
    fun `rejects a value that isn't exactly 11 digits`() {
        assertFalse(isValidAbnChecksum("5182475355"))
        assertFalse(isValidAbnChecksum("518247535566"))
        assertFalse(isValidAbnChecksum(""))
    }

    @Test
    fun `rejects non-numeric input`() {
        assertFalse(isValidAbnChecksum("ABCDEFGHIJK"))
    }
}
