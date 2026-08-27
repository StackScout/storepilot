package com.storepilot.backend.abn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AbnLookupServiceTest {
    @Test
    fun `lookup reports invalid-format without ever needing a GUID`() {
        val service = AbnLookupService(AbrProperties(guid = ""))

        val result = service.lookup("12345678901")

        assertEquals(AbnLookupStatus.INVALID_FORMAT.wireValue, result.status)
    }

    @Test
    fun `lookup reports not-configured for a valid ABN when no GUID is set`() {
        val service = AbnLookupService(AbrProperties(guid = ""))

        val result = service.lookup("51824753556")

        assertEquals(AbnLookupStatus.NOT_CONFIGURED.wireValue, result.status)
    }
}
