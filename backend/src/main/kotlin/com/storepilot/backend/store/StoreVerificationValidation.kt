package com.storepilot.backend.store

import com.storepilot.backend.abn.isValidAbnChecksum

/**
 * A store's seller-identity verification fields are country-specific (see
 * StoreSettings' doc comment) — [countryCode] (this deployment's
 * platform_settings.country_code) decides which pair is required, never
 * both. The business-only field (ABN / business registration number) is
 * only required when [sellerType] is BUSINESS. Shared by StoreService's
 * direct settings upsert (pre-approval submissions) and
 * StoreVerificationChangeRequestService (post-approval change requests),
 * so the two paths can never drift apart on what "valid" means.
 */
fun requireCountryVerificationFields(
    countryCode: String,
    sellerType: SellerType,
    driverLicenceNumber: String?,
    abn: String?,
    nicNumber: String?,
    businessRegistrationNumber: String?,
    // Registering for GST with the ATO always issues an ABN, regardless of
    // whether the registrant is a sole trader (sellerType INDIVIDUAL) or a
    // company (BUSINESS) — so this check is independent of the
    // sellerType == BUSINESS branch below, which only covers the
    // business-registration-specific requirement. Without this, a GST-
    // registered individual seller's order confirmations render as tax
    // invoices with an "Includes GST" line but no ABN, which isn't a valid
    // ATO tax invoice. Defaults to false for Sri Lanka's branch since GST
    // tax invoices are an Australia-only feature (see StoreSettings
    // .gstRegistered's doc comment).
    gstRegistered: Boolean = false,
) {
    if (countryCode == "LK") {
        require(!nicNumber.isNullOrBlank()) { "NIC number is required" }
        if (sellerType == SellerType.BUSINESS) {
            require(!businessRegistrationNumber.isNullOrBlank()) {
                "Business registration number is required for a registered business"
            }
        }
    } else {
        require(!driverLicenceNumber.isNullOrBlank()) { "Driver's licence number is required" }
        if (sellerType == SellerType.BUSINESS) {
            require(!abn.isNullOrBlank()) { "ABN is required for a registered business" }
            require(isValidAbnChecksum(abn!!)) { "Enter a valid ABN" }
        } else if (gstRegistered) {
            require(!abn.isNullOrBlank()) { "An ABN is required to register for GST" }
            require(isValidAbnChecksum(abn!!)) { "Enter a valid ABN" }
        }
    }
}
