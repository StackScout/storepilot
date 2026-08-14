package com.storepilot.backend.payout

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.store.Store
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The reverse ledger from Payout — COD and bank-transfer both pay the
 * seller directly (bank-transfer literally shows the seller's own bank
 * details at checkout), so for those the platform is owed its fee *by* the
 * seller, not the other way around. Same admin-batches-then-marks-settled
 * shape as Payout, just tracking what's owed rather than what's payable.
 * Never touched by Stripe orders — see PaymentMethod.STRIPE's doc comment.
 */
@Entity
@Table(name = "fee_collections")
class FeeCollection(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @OneToMany(mappedBy = "feeCollection", cascade = [CascadeType.ALL], orphanRemoval = true)
    var sourceRefs: MutableList<FeeCollectionSourceRef> = mutableListOf(),
    /** Sum of included orders'/bookings' subtotal — informational context alongside platformFee, not itself owed. */
    @Column(nullable = false)
    var subtotal: Int,
    /** Sum of included orders'/bookings' platformFee — the actual amount owed to the platform. */
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
    @Column(nullable = false)
    var status: FeeCollectionStatus = FeeCollectionStatus.PENDING,
    @Column(name = "collected_at")
    var collectedAt: Instant? = null,
    /** Admin-recorded reference once collected (an invoice number, a bank reference for a seller-initiated transfer, etc.) — free text, same as Payout.bankReference. */
    var reference: String? = null,
) : BaseEntity()

/** Mirrors PayoutSourceRef — a snapshot of each included order's/booking's totals at batch-creation time, polymorphic the same way. */
@Entity
@Table(name = "fee_collection_order_refs")
class FeeCollectionSourceRef(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_collection_id", nullable = false)
    var feeCollection: FeeCollection,
    @Column(name = "order_id")
    var orderId: UUID? = null,
    @Column(name = "order_number")
    var orderNumber: String? = null,
    @Column(name = "booking_id")
    var bookingId: UUID? = null,
    @Column(name = "booking_number")
    var bookingNumber: String? = null,
    @Column(nullable = false)
    var subtotal: Int,
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
) : BaseEntity()
