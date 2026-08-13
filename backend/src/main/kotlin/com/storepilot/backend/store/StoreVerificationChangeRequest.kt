package com.storepilot.backend.store

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * A seller's proposed change to their store's already-approved identity-
 * verification fields (sellerType/driverLicenceNumber/abn/nicNumber/
 * businessRegistrationNumber, and their supporting documents) — created
 * once a store is ACTIVE, since edits before that point still apply
 * directly via StoreService.upsertSettings (see
 * StoreVerificationChangeRequestService's doc comment for the full
 * policy). Nothing here takes effect on the real StoreSettings row until
 * an admin approves it; only one request may be PENDING per store at a
 * time (enforced in the service layer, not a DB constraint). Every
 * proposed field is nullable — a submission only needs to include what's
 * actually changing, same "partial patch" convention as StoreSettingsInput.
 * *DocumentUrl fields are FileStorageService references (never a resolved
 * URL), same convention as StoreSettings' own document fields — resolved
 * fresh at read time.
 */
@Entity
@Table(name = "store_verification_change_requests")
class StoreVerificationChangeRequest(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(nullable = false)
    var status: StoreVerificationChangeRequestStatus = StoreVerificationChangeRequestStatus.PENDING,
    @Column(name = "seller_type")
    var sellerType: SellerType? = null,
    @Column(name = "driver_licence_number")
    var driverLicenceNumber: String? = null,
    @Column(name = "abn")
    var abn: String? = null,
    @Column(name = "nic_number")
    var nicNumber: String? = null,
    @Column(name = "business_registration_number")
    var businessRegistrationNumber: String? = null,
    @Column(name = "driver_licence_document_url")
    var driverLicenceDocumentUrl: String? = null,
    @Column(name = "abn_document_url")
    var abnDocumentUrl: String? = null,
    @Column(name = "nic_document_url")
    var nicDocumentUrl: String? = null,
    @Column(name = "business_reg_document_url")
    var businessRegDocumentUrl: String? = null,
    @Column(name = "rejection_reason", columnDefinition = "text")
    var rejectionReason: String? = null,
    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,
    @Column(name = "reviewed_by_email")
    var reviewedByEmail: String? = null,
) : BaseEntity()
