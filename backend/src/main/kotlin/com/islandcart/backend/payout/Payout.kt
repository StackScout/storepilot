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
 * Mirrors src/types/payout.ts's Payout. `storeName` is NOT duplicated —
 * derive from the `store` relation. Created and released only by the admin
 * side (see docs/features/payouts.md) — never written to by seller-facing
 * code.
 */
@Entity
@Table(name = "payouts")
class Payout(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @OneToMany(mappedBy = "payout", cascade = [CascadeType.ALL], orphanRemoval = true)
    var orders: MutableList<PayoutOrderRef> = mutableListOf(),
    @Column(name = "subtotal_lkr", nullable = false)
    var subtotalLkr: Int,
    @Column(name = "platform_fee_lkr", nullable = false)
    var platformFeeLkr: Int,
    @Column(name = "net_lkr", nullable = false)
    var netLkr: Int,
    @Column(nullable = false)
    var status: PayoutStatus = PayoutStatus.SCHEDULED,
    @Column(name = "paid_at")
    var paidAt: Instant? = null,
    @Column(name = "bank_reference")
    var bankReference: String? = null,
) : BaseEntity()

/**
 * Mirrors src/types/payout.ts's PayoutOrderRef — a snapshot of each included
 * order's totals at the time the payout batch was created, so a payout's
 * amount stays accurate even if the underlying Order somehow changed later.
 * `orderId` is a plain UUID, not a foreign key, for the same reason as
 * OrderItem.productId.
 */
@Entity
@Table(name = "payout_order_refs")
class PayoutOrderRef(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payout_id", nullable = false)
    var payout: Payout,
    @Column(name = "order_id", nullable = false)
    var orderId: UUID,
    @Column(name = "order_number", nullable = false)
    var orderNumber: String,
    @Column(name = "subtotal_lkr", nullable = false)
    var subtotalLkr: Int,
    @Column(name = "platform_fee_lkr", nullable = false)
    var platformFeeLkr: Int,
    @Column(name = "net_lkr", nullable = false)
    var netLkr: Int,
) : BaseEntity()
