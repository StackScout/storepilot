package com.storepilot.backend.common

import java.math.BigDecimal

/** Shape the frontend fetches from GET /api/platform-config instead of baking country content into NEXT_PUBLIC_* build args. */
data class PlatformConfigResponse(
    val name: String,
    val tagline: String,
    val countryName: String,
    val countryCode: String,
    val currencyCode: String,
    val currencySymbol: String,
    val currencyLocale: String,
    val platformFeePercent: BigDecimal,
    val flatShippingFee: Int,
    val proMonthlyPriceCents: Int,
    val defaultCodEnabled: Boolean,
    val defaultOnlinePaymentEnabled: Boolean,
    val defaultBankTransferEnabled: Boolean,
    val proPlanEnabled: Boolean,
    val supportEmail: String,
    val companyLocation: String,
    val returnWindowDays: Int,
)

/** PATCH /api/admin/platform-config/payment-methods body — see PlatformSettings' default*Enabled doc comments for what these actually gate. */
data class PlatformPaymentMethodsInput(
    val codEnabled: Boolean,
    val onlinePaymentEnabled: Boolean,
    val bankTransferEnabled: Boolean,
)

/** PATCH /api/admin/platform-config/pro-plan body — see PlatformSettings.proPlanEnabled's doc comment. */
data class PlatformProPlanInput(
    val enabled: Boolean,
)

fun PlatformSettings.toResponse(): PlatformConfigResponse =
    PlatformConfigResponse(
        name = name,
        tagline = tagline,
        countryName = countryName,
        countryCode = countryCode,
        currencyCode = currencyCode,
        currencySymbol = currencySymbol,
        currencyLocale = currencyLocale,
        platformFeePercent = platformFeePercent,
        flatShippingFee = flatShippingFee,
        proMonthlyPriceCents = proMonthlyPriceCents,
        defaultCodEnabled = defaultCodEnabled,
        defaultOnlinePaymentEnabled = defaultOnlinePaymentEnabled,
        defaultBankTransferEnabled = defaultBankTransferEnabled,
        proPlanEnabled = proPlanEnabled,
        supportEmail = supportEmail,
        companyLocation = companyLocation,
        returnWindowDays = returnWindowDays,
    )

data class StateResponse(val name: String)
