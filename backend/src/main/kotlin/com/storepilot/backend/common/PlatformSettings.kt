package com.storepilot.backend.common

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
    /** Cents — see SellerPlan.kt. */
    @Column(name = "pro_monthly_price_cents", nullable = false)
    var proMonthlyPriceCents: Int,
    /**
     * Dual purpose: (1) the seed default handed to a new store's own
     * StoreSettings toggle at onboarding (StoreService.create) when the
     * seller doesn't specify one, and (2) — since this deployment is
     * admin-editable via PATCH /api/admin/platform-config/payment-methods
     * (see PlatformConfigController) — the platform-wide ceiling on
     * whether this payment method can be used at all in this
     * country/deployment, enforced in OrderService.createOrder regardless
     * of what an individual store has toggled on. A store's own toggle
     * only decides whether *that store* accepts the method; this decides
     * whether the method exists in this market at all (e.g. an AU
     * deployment turning this off for cod/bank-transfer since it only
     * ever uses Stripe).
     */
    @Column(name = "default_cod_enabled", nullable = false)
    var defaultCodEnabled: Boolean,
    /** See [defaultCodEnabled]'s doc comment — same dual role, for Stripe/PayHere (whichever this deployment's country wires up as "online payment"). */
    @Column(name = "default_online_payment_enabled", nullable = false)
    var defaultOnlinePaymentEnabled: Boolean,
    /** See [defaultCodEnabled]'s doc comment — same dual role, for bank transfer. */
    @Column(name = "default_bank_transfer_enabled", nullable = false)
    var defaultBankTransferEnabled: Boolean,
    /**
     * Whether the seller Free/Pro tier concept exists at all on this
     * deployment — admin-editable via PATCH
     * /api/admin/platform-config/pro-plan (see PlatformConfigController).
     * When false: every Pro-only gate (SellerPlan.PRO checks in
     * OrderService/BookingService/StoreService for cod/bank-transfer, and
     * BookingAnalyticsService's premium-analytics check) is bypassed
     * platform-wide, and every Pro-plan UI surface (onboarding's plan
     * picker, the billing/upgrade pages, sidebar Pro badges, inline
     * "Pro-only" captions) is hidden on web and mobile — see
     * usePlatformConfig()'s proPlanEnabled. Added for deployments (e.g.
     * AU) that don't use tiered seller plans at all, so a feature doesn't
     * end up permanently locked behind an upgrade path nobody can take
     * (there being no billing UI to take it from).
     */
    @Column(name = "pro_plan_enabled", nullable = false)
    var proPlanEnabled: Boolean,
    @Column(name = "support_email", nullable = false)
    var supportEmail: String,
    @Column(name = "company_location", nullable = false)
    var companyLocation: String,
    /** IANA zone id — see PlatformProperties.timezone's doc comment for why this is deployment-wide, not per-store. */
    @Column(nullable = false)
    var timezone: String,
    /** See PlatformProperties.returnWindowDays' doc comment. */
    @Column(name = "return_window_days", nullable = false)
    var returnWindowDays: Int,
) : BaseEntity()
