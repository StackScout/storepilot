package com.storepilot.backend.store

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.URL
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class StoreAddressResponse(
    val city: String,
    val state: String,
)

/** Shape matches src/types/store.ts's Store exactly — `joinedAt` maps from BaseEntity.createdAt. */
data class StoreResponse(
    val id: UUID,
    val slug: String,
    val name: String,
    val tagline: String,
    val description: String,
    val logoUrl: String?,
    val bannerUrl: String?,
    val category: String,
    val address: StoreAddressResponse,
    val whatsappNumber: String,
    val rating: Double,
    val reviewCount: Int,
    val productCount: Int,
    val isVerified: Boolean,
    val joinedAt: Instant,
    val followerCount: Int,
    val verificationStatus: String,
    val facebookUrl: String?,
    val instagramUrl: String?,
    val tiktokUrl: String?,
)

/** Mirrors src/types/store.ts's StoreSettings. */
data class StoreSettingsResponse(
    val storeId: UUID,
    val contactEmail: String,
    val contactPhone: String,
    val bankAccountName: String,
    val bankAccountNumber: String,
    val bankName: String,
    val transactionFeePercent: BigDecimal,
    val codEnabled: Boolean,
    val onlinePaymentEnabled: Boolean,
    val bankTransferEnabled: Boolean,
    val sellerType: String,
    val driverLicenceNumber: String?,
    val abn: String?,
    val nicNumber: String?,
    val businessRegistrationNumber: String?,
    val rejectionReason: String?,
    val driverLicenceDocumentUrl: String?,
    val abnDocumentUrl: String?,
    val nicDocumentUrl: String?,
    val businessRegDocumentUrl: String?,
    val stockManagementEnabled: Boolean,
    val pickupEnabled: Boolean,
    val stripeAccountId: String?,
    val stripeChargesEnabled: Boolean,
    val stripePayoutsEnabled: Boolean,
    val stripeEnabled: Boolean,
)

/** Mirrors src/types/store.ts's StoreApplicationInput — POST /api/stores (onboarding). */
data class StoreApplicationInput(
    @field:NotBlank(message = "Enter your store name")
    val name: String,
    @field:NotBlank(message = "Select a category")
    val category: String,
    @field:NotBlank(message = "Add a short tagline")
    val tagline: String,
    @field:NotBlank(message = "Describe your store")
    val description: String,
    @field:NotBlank(message = "Enter your city/town")
    val city: String,
    @field:NotBlank(message = "Select a state/province")
    val state: String,
    @field:NotBlank(message = "Enter a valid WhatsApp number")
    val whatsappNumber: String,
)

/**
 * Mirrors the frontend's `Partial<Omit<StoreSettings, "storeId">>` PATCH
 * body — every field optional so the same DTO covers both onboarding's
 * initial write and the seller settings page's later edits (an upsert
 * either way, see StoreService).
 */
data class StoreSettingsInput(
    @field:Email(message = "Enter a valid email")
    val contactEmail: String? = null,
    @field:Size(min = 9, message = "Enter a valid phone number")
    val contactPhone: String? = null,
    val bankAccountName: String? = null,
    val bankAccountNumber: String? = null,
    val bankName: String? = null,
    @field:DecimalMin(value = "0.0", message = "Transaction fee must be zero or more")
    @field:DecimalMax(value = "100.0", message = "Transaction fee can't exceed 100%")
    val transactionFeePercent: BigDecimal? = null,
    val codEnabled: Boolean? = null,
    val onlinePaymentEnabled: Boolean? = null,
    val bankTransferEnabled: Boolean? = null,
    val sellerType: String? = null,
    val driverLicenceNumber: String? = null,
    val abn: String? = null,
    val nicNumber: String? = null,
    val businessRegistrationNumber: String? = null,
    val rejectionReason: String? = null,
    val stockManagementEnabled: Boolean? = null,
    val pickupEnabled: Boolean? = null,
    /** The seller's own on/off preference — stripeAccountId/stripeChargesEnabled/stripePayoutsEnabled are never client-settable, only synced from Stripe via webhook (see StripeConnectService). */
    val stripeEnabled: Boolean? = null,
)

/** PATCH /api/stores/{storeId}/profile — seller-editable public social links. A field left null is untouched; send an empty string to clear a link. */
data class StoreProfileInput(
    @field:URL(message = "Enter a valid URL")
    val facebookUrl: String? = null,
    @field:URL(message = "Enter a valid URL")
    val instagramUrl: String? = null,
    @field:URL(message = "Enter a valid URL")
    val tiktokUrl: String? = null,
)

data class VerificationDecisionInput(
    @field:NotBlank(message = "Status is required")
    val status: String,
    val rejectionReason: String? = null,
)
