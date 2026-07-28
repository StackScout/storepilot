package com.islandcart.backend.payout

import com.islandcart.backend.common.BaseEntity
import com.islandcart.backend.store.Store
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
    var orders: MutableList<FeeCollectionOrderRef> = mutableListOf(),
    /** Sum of included orders' subtotal — informational context alongside platformFee, not itself owed. */
    @Column(nullable = false)
    var subtotal: Int,
    /** Sum of included orders' platformFee — the actual amount owed to the platform. */
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
    @Column(nullable = false)
    var status: FeeCollectionStatus = FeeCollectionStatus.PENDING,
    @Column(name = "collected_at")
    var collectedAt: Instant? = null,
    /** Admin-recorded reference once collected (an invoice number, a bank reference for a seller-initiated transfer, etc.) — free text, same as Payout.bankReference. */
    var reference: String? = null,
) : BaseEntity()

/** Mirrors PayoutOrderRef — a snapshot of each included order's totals at batch-creation time. */
@Entity
@Table(name = "fee_collection_order_refs")
class FeeCollectionOrderRef(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_collection_id", nullable = false)
    var feeCollection: FeeCollection,
    @Column(name = "order_id", nullable = false)
    var orderId: UUID,
    @Column(name = "order_number", nullable = false)
    var orderNumber: String,
    @Column(nullable = false)
    var subtotal: Int,
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
) : BaseEntity()
