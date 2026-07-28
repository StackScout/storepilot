package com.islandcart.backend.common

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * The live, DB-backed platform configuration — a single row. Seeded once
 * from PlatformProperties' bootstrap env-var values by DataSeeder if the
 * table is empty; from then on this row (not PlatformProperties) is what
 * the running app reads, so a deployment can be reconfigured by updating
 * this row directly, without rebuilding or redeploying any container.
 * Exposed to the frontend via GET /api/platform-config
 * (PlatformConfigController) so NEXT_PUBLIC_* build-time env vars are no
 * longer needed for this content.
 */
@Entity
@Table(name = "platform_settings")
class PlatformSettings(
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var tagline: String,
    @Column(name = "country_name", nullable = false)
    var countryName: String,
    @Column(name = "country_code", nullable = false)
    var countryCode: String,
    @Column(name = "currency_code", nullable = false)
    var currencyCode: String,
    @Column(name = "currency_symbol", nullable = false)
    var currencySymbol: String,
    @Column(name = "currency_locale", nullable = false)
    var currencyLocale: String,
    @Column(name = "platform_fee_percent", nullable = false)
    var platformFeePercent: BigDecimal,
    /** Cents, like every other money field in this codebase — see Product.price's doc comment. */
    @Column(name = "flat_shipping_fee", nullable = false)
    var flatShippingFee: Int,
    @Column(name = "default_cod_enabled", nullable = false)
    var defaultCodEnabled: Boolean,
    @Column(name = "default_online_payment_enabled", nullable = false)
    var defaultOnlinePaymentEnabled: Boolean,
    @Column(name = "default_bank_transfer_enabled", nullable = false)
    var defaultBankTransferEnabled: Boolean,
    @Column(name = "support_email", nullable = false)
    var supportEmail: String,
    @Column(name = "company_location", nullable = false)
    var companyLocation: String,
) : BaseEntity()
