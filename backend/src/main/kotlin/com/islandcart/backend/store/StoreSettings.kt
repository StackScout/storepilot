package com.islandcart.backend.store

import com.islandcart.backend.common.BaseEntity
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
    @Column(name = "nic_number", nullable = false)
    var nicNumber: String,
    @Column(name = "business_registration_number")
    var businessRegistrationNumber: String? = null,
    @Column(name = "rejection_reason", columnDefinition = "text")
    var rejectionReason: String? = null,
) : BaseEntity()
