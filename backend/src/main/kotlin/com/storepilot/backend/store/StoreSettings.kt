package com.storepilot.backend.store

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * Mirrors src/types/store.ts's StoreSettings — a sparse 1:1 child of Store
 * (shares Store's primary key via @MapsId), not every Store has one. The
 * mock's "upsert" semantics (create-if-missing) belong in the service layer,
 * not this entity.
 */
@Entity
@Table(name = "store_settings")
class StoreSettings(
    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "store_id")
    var store: Store,
    @Column(name = "contact_email", nullable = false)
    var contactEmail: String,
    @Column(name = "contact_phone", nullable = false)
    var contactPhone: String,
    @Column(name = "bank_account_name", nullable = false)
    var bankAccountName: String,
    @Column(name = "bank_account_number", nullable = false)
    var bankAccountNumber: String,
    @Column(name = "bank_name", nullable = false)
    var bankName: String,
    @Column(name = "transaction_fee_percent", nullable = false, precision = 5, scale = 2)
    var transactionFeePercent: BigDecimal,
    @Column(name = "cod_enabled", nullable = false)
    var codEnabled: Boolean = true,
    @Column(name = "online_payment_enabled", nullable = false)
    var onlinePaymentEnabled: Boolean = true,
    // Opt-in and off by default — unlike COD/PayHere this exposes the
    // seller's bank details to buyers, so it shouldn't switch on silently
    // just because bankName/bankAccountNumber were already filled in for payouts.
    @Column(name = "bank_transfer_enabled", nullable = false)
    var bankTransferEnabled: Boolean = false,
    @Column(name = "seller_type", nullable = false)
    var sellerType: SellerType,
    /**
     * Individual-seller identity verification fields — which pair is
     * required/shown is decided per-deployment by `platform_settings.country_code`
     * (see StoreService's country-conditional validation), never both at
     * once for a given store. Australia: driver's licence number. Sri
     * Lanka: National Identity Card number. Both are nullable since a given
     * deployment only ever populates the pair matching its own country.
     */
    @Column(name = "driver_licence_number")
    var driverLicenceNumber: String? = null,
    /** Australian Business Number — required when sellerType === "business" on an AU deployment. */
    @Column(name = "abn")
    var abn: String? = null,
    /** Sri Lanka NIC number — required when a deployment's country_code is "LK". */
    @Column(name = "nic_number")
    var nicNumber: String? = null,
    /** Sri Lanka Business Registration Number — required when sellerType === "business" on an LK deployment. */
    @Column(name = "business_registration_number")
    var businessRegistrationNumber: String? = null,
    @Column(name = "rejection_reason", columnDefinition = "text")
    var rejectionReason: String? = null,
    /** Stored reference (local path or S3 key) from FileStorageService — resolve via resolveUrl() at read time, never persist a fixed URL. */
    @Column(name = "driver_licence_document_url")
    var driverLicenceDocumentUrl: String? = null,
    @Column(name = "abn_document_url")
    var abnDocumentUrl: String? = null,
    @Column(name = "nic_document_url")
    var nicDocumentUrl: String? = null,
    @Column(name = "business_reg_document_url")
    var businessRegDocumentUrl: String? = null,
    /** Store-wide switch — when false, no product in this store tracks stock, regardless of each Product.trackStock; the new-product page hides the stock UI entirely. */
    @Column(name = "stock_management_enabled", nullable = false)
    var stockManagementEnabled: Boolean = true,
    /** Opt-in and off by default, same reasoning as bankTransferEnabled/stripeEnabled — not every seller has a physical location buyers can collect from. See DeliveryMethod's doc comment. */
    @Column(name = "pickup_enabled", nullable = false)
    var pickupEnabled: Boolean = false,
    /** Stripe Connect (Standard account) — the seller's own connected account id, `acct_...`. Null until they start onboarding. See StripeConnectService. */
    @Column(name = "stripe_account_id")
    var stripeAccountId: String? = null,
    /**
     * Mirror the connected account's own `charges_enabled`/`payouts_enabled`
     * — kept in sync via the `account.updated` webhook, never inferred from
     * "an account id exists". This is the actual source of truth for
     * whether Stripe checkout can be offered, checked server-side by
     * StripeService, not just gated in the frontend.
     */
    @Column(name = "stripe_charges_enabled", nullable = false)
    var stripeChargesEnabled: Boolean = false,
    @Column(name = "stripe_payouts_enabled", nullable = false)
    var stripePayoutsEnabled: Boolean = false,
    /** The seller's own on/off preference for offering Stripe at checkout — independent of onboarding status, so they can pause it without disconnecting. Opt-in/off-by-default, same reasoning as bankTransferEnabled. */
    @Column(name = "stripe_enabled", nullable = false)
    var stripeEnabled: Boolean = false,
    /** Opt-in and off by default, same reasoning as pickupEnabled — most stores sell products only. Gates whether the store's bookable-services section exists at all; not Pro-gated itself (only the "pay at venue"/bank-transfer booking payment methods are, mirroring codEnabled/bankTransferEnabled — see BookingService). */
    @Column(name = "bookings_enabled", nullable = false)
    var bookingsEnabled: Boolean = false,
    /**
     * Self-declared, opt-in, off by default — GST registration is
     * turnover-based (mandatory above A$75,000/year, optional below it),
     * not something ABN presence implies. Only meaningful for AU sellers,
     * but not itself country-gated here (mirrors abn/nicNumber — the
     * deployment's country decides which fields are shown/required, not
     * the entity). Read at order-creation time by OrderService to decide
     * whether to snapshot a tax invoice's sellerAbn/gstAmount onto the
     * Order — see Order.kt's doc comment on those fields.
     */
    @Column(name = "gst_registered", nullable = false)
    var gstRegistered: Boolean = false,
) : BaseEntity()
