package com.storepilot.backend.common

import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Inserts the single `platform_settings` row from PlatformProperties' bootstrap
 * env-var values on first boot — a no-op on every later boot. This is what
 * makes the DB (not application.yml) the running app's actual config source,
 * and what an operator would edit directly to reconfigure a deployment
 * without rebuilding/redeploying. See PlatformSettings/PlatformConfigService's
 * doc comments — every request to PlatformConfigService.current() fails hard
 * if this row is missing, so unlike DataSeeder (demo marketplace content,
 * `@Profile("!aws")` only), this runs in every profile, including `aws`.
 */
@Component
class PlatformSettingsSeeder(
    private val platformSettingsRepository: PlatformSettingsRepository,
    private val platformProperties: PlatformProperties,
) : CommandLineRunner {
    private val log = LoggerFactory.getLogger(PlatformSettingsSeeder::class.java)

    @Transactional
    override fun run(vararg args: String) {
        if (platformSettingsRepository.count() > 0) return
        platformSettingsRepository.save(
            PlatformSettings(
                name = platformProperties.name,
                tagline = platformProperties.tagline,
                countryName = platformProperties.countryName,
                countryCode = platformProperties.countryCode,
                currencyCode = platformProperties.currencyCode,
                currencySymbol = platformProperties.currencySymbol,
                currencyLocale = platformProperties.currencyLocale,
                platformFeePercent = platformProperties.platformFeePercent,
                flatShippingFee = platformProperties.flatShippingFee,
                proMonthlyPriceCents = platformProperties.proMonthlyPriceCents,
                defaultCodEnabled = platformProperties.defaultCodEnabled,
                defaultOnlinePaymentEnabled = platformProperties.defaultOnlinePaymentEnabled,
                defaultBankTransferEnabled = platformProperties.defaultBankTransferEnabled,
                proPlanEnabled = platformProperties.proPlanEnabled,
                supportEmail = platformProperties.supportEmail,
                companyLocation = platformProperties.companyLocation,
                timezone = platformProperties.timezone,
                returnWindowDays = platformProperties.returnWindowDays,
            ),
        )
        log.info("Seeded platform_settings from bootstrap PlatformProperties (name={}).", platformProperties.name)
    }
}
