package com.storepilot.backend.common

import org.springframework.boot.context.properties.ConfigurationProperties
import java.math.BigDecimal

/**
 * Bound from PLATFORM_* env vars (application.yml) — **bootstrap values
 * only**. DataSeeder reads this exactly once, to insert the single
 * `platform_settings` row if the table is empty; from then on the running
 * app reads that DB row (via PlatformConfigService), never this class
 * directly. This is what lets a deployment be reconfigured later (a new
 * country, a fee change) by updating the DB row instead of rebuilding and
 * redeploying every container.
 *
 * Every money field in this codebase (here and elsewhere — Product.price,
 * Order.total, ...) is an Int count of the currency's smallest unit (cents
 * for AUD), not whole dollars — see Product.price's doc comment.
 */
@ConfigurationProperties(prefix = "platform")
data class PlatformProperties(
    val name: String = "StorePilot",
    /**
     * Defaults below are Australia's, not Sri Lanka's — the SL launch is
     * on hold pending business registration/legal setup, so AU is the
     * near-term deployment this codebase actually needs to boot as by
     * default. A Sri Lanka deployment overrides these via env vars, the
     * same way an AU deployment would have before this switch.
     */
    val tagline: String = "Australia's marketplace for small business sellers",
    /** Plain country name (not a currency/locale code) — consumed by PayHereService's checkout payload. */
    val countryName: String = "Australia",
    /** ISO 3166-1 alpha-2 — used to select this deployment's row in the `states` reference table (see State.kt) and as the order-number prefix (see OrderService.generateOrderNumber). */
    val countryCode: String = "AU",
    /** ISO 4217 currency code — sent to PayHere as-is, and used in plain-text email copy (e.g. "Total: AUD 100"). */
    val currencyCode: String = "AUD",
    /** Symbol prefix for backend-generated plain-text (emails) and the frontend's Intl.NumberFormat-driven display. */
    val currencySymbol: String = "$",
    val currencyLocale: String = "en-AU",
    val platformFeePercent: BigDecimal = BigDecimal("2.0"),
    /** Cents — $10.00. */
    val flatShippingFee: Int = 1000,
    /** Cents — $9.90/month. See SellerPlan.kt/SellerBillingService for what Pro actually unlocks. */
    val proMonthlyPriceCents: Int = 990,
    /**
     * Defaults applied to a newly-onboarded store's StoreSettings — a
     * seller can still change these afterward via store settings, subject
     * to requireAtLeastOnePaymentMethod. Online payment (PayHereService) is
     * a Sri Lanka-specific gateway integration with no AU equivalent wired
     * up yet (an AU deployment needs Stripe Connect — not built in this
     * pass), so it defaults OFF here to avoid advertising a payment method
     * that doesn't actually work for an AU seller; COD/bank-transfer are
     * the only payment methods this codebase can genuinely offer AU
     * sellers today.
     */
    val defaultCodEnabled: Boolean = true,
    val defaultOnlinePaymentEnabled: Boolean = false,
    val defaultBankTransferEnabled: Boolean = true,
    val supportEmail: String = "hello@storepilot.au",
    val companyLocation: String = "Sydney, Australia",
    /**
     * IANA zone id used to convert a resolved weekly-availability window
     * (LocalTime) into absolute booking-slot Instants — see
     * AvailabilityService.computeSlots. Deployment-wide, not per-store: this
     * codebase is already single-deployment-per-country (PayHere=LK-only,
     * Stripe=AU-only), so a single zone matches every other country-specific
     * default here rather than adding a per-store timezone field.
     */
    val timezone: String = "Australia/Sydney",
)
