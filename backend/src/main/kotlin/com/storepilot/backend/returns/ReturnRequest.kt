package com.storepilot.backend.returns

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.order.Order
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * A buyer's post-delivery return/refund request against one order — own
 * entity rather than a field on Order, same "proposed change needing a
 * decision" pattern as StoreVerificationChangeRequest. Order.status is
 * never touched by this feature (the ALLOWED_STATUS_TRANSITIONS state
 * machine in OrderService is closed once DELIVERED); only
 * Order.paymentStatus flips PAID -> REFUNDED, once money has actually
 * moved (or, for Stripe, synchronously on approval — see
 * ReturnRequestService.decide).
 *
 * settlementReconciliationNote is snapshotted once, at seller-approval
 * time, if the order was already included in a Payout/FeeCollection batch
 * — same "freeze at creation time" principle as PayoutSourceRef's own
 * subtotal/platformFee/net snapshot. There is no automatic clawback for an
 * already-PAID/COLLECTED batch in this codebase; this note is a visible
 * flag for manual admin reconciliation, not a fix.
 */
@Entity
@Table(name = "return_requests")
class ReturnRequest(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,
    @Column(name = "reason_category", nullable = false)
    var reasonCategory: ReturnReasonCategory,
    @Column(name = "reason_note", columnDefinition = "text")
    var reasonNote: String? = null,
    @Column(nullable = false)
    var status: ReturnRequestStatus = ReturnRequestStatus.REQUESTED,
    @Column(name = "seller_decision_note", columnDefinition = "text")
    var sellerDecisionNote: String? = null,
    @Column(name = "refund_reference")
    var refundReference: String? = null,
    @Column(name = "settlement_reconciliation_note", columnDefinition = "text")
    var settlementReconciliationNote: String? = null,
    @Column(name = "decided_at")
    var decidedAt: Instant? = null,
    @Column(name = "refunded_at")
    var refundedAt: Instant? = null,
) : BaseEntity()
