package com.islandcart.backend.common

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
    val defaultCodEnabled: Boolean,
    val defaultOnlinePaymentEnabled: Boolean,
    val defaultBankTransferEnabled: Boolean,
    val supportEmail: String,
    val companyLocation: String,
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
        defaultCodEnabled = defaultCodEnabled,
        defaultOnlinePaymentEnabled = defaultOnlinePaymentEnabled,
        defaultBankTransferEnabled = defaultBankTransferEnabled,
        supportEmail = supportEmail,
        companyLocation = companyLocation,
    )

data class StateResponse(val name: String)
