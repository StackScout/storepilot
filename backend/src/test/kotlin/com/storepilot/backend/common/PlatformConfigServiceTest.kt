package com.storepilot.backend.common

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PlatformConfigServiceTest {
    private val repository = mockk<PlatformSettingsRepository>()

    private val service = PlatformConfigService(repository)

    private fun settings() = PlatformSettings(
        name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
        currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
        flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = true, defaultOnlinePaymentEnabled = false,
        defaultBankTransferEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
        timezone = "Australia/Sydney", returnWindowDays = 14,
    )

    @Test
    fun `current returns the single settings row`() {
        val row = settings()
        every { repository.findAll() } returns listOf(row)

        assertEquals(row, service.current())
    }

    @Test
    fun `current errors out when no row exists`() {
        every { repository.findAll() } returns emptyList()

        assertThrows(IllegalStateException::class.java) { service.current() }
    }
}
