package com.storepilot.backend.order

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    /** Unpaged — used where the full set is genuinely needed (e.g. PayoutService's eligible-orders scan). See the Pageable overloads below for the seller dashboard's order list. */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Order>

    fun findByStoreIdAndStatusOrderByCreatedAtDesc(storeId: UUID, status: OrderStatus): List<Order>

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<Order>

    fun findByStoreIdAndStatusOrderByCreatedAtDesc(storeId: UUID, status: OrderStatus, pageable: Pageable): Page<Order>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Order>

    /** Close-store precondition check — see StoreService.closeStore. */
    fun existsByStoreIdAndStatusIn(storeId: UUID, statuses: Collection<OrderStatus>): Boolean

    /** Verified-purchase gate for a product review — see ReviewService.createProductReview. */
    fun existsByBuyerIdAndStatusAndItems_ProductId(buyerId: UUID, status: OrderStatus, productId: UUID): Boolean

    /** Verified-purchase gate for a store review — see ReviewService.createStoreReview. */
    fun existsByBuyerIdAndStoreIdAndStatus(buyerId: UUID, storeId: UUID, status: OrderStatus): Boolean

    /** Stripe Connect direct charges auto-settle at charge time (see PaymentMethod.STRIPE's doc comment) — this is a read-only reconciliation view, not a ledger like Payout/FeeCollection. */
    fun findByStoreIdAndPaymentMethodAndPaymentStatusOrderByCreatedAtDesc(storeId: UUID, paymentMethod: PaymentMethod, paymentStatus: PaymentStatus): List<Order>

    fun findByPaymentMethodAndPaymentStatusOrderByCreatedAtDesc(paymentMethod: PaymentMethod, paymentStatus: PaymentStatus): List<Order>

    fun findByOrderNumberIgnoreCase(orderNumber: String): Order?

    /**
     * Candidate pool for OrderFulfillmentReminderJob — every not-yet-shipped
     * order that hasn't had both its due-soon and overdue reminder fired
     * yet. The job itself computes each order's own deadline
     * (createdAt + fulfillmentTimeHours) and decides which (if either)
     * reminder is actually due — can't push that into SQL cleanly since
     * "due soon" also depends on notifications.fulfillment-due-soon-lead-hours.
     */
    @Query(
        """
        select o from Order o
        where o.status in (com.storepilot.backend.order.OrderStatus.PENDING, com.storepilot.backend.order.OrderStatus.CONFIRMED)
          and (o.fulfillmentReminderSentAt is null or o.fulfillmentOverdueReminderSentAt is null)
        """,
    )
    fun findCandidatesForFulfillmentReminder(): List<Order>

    /** Candidate pool for OrderDeliveryReminderJob — see its doc comment for why shippedAt (not createdAt) is the delivery clock's start. */
    @Query(
        """
        select o from Order o
        where o.status = com.storepilot.backend.order.OrderStatus.SHIPPED
          and o.deliveryReminderSentAt is null
          and o.shippedAt is not null
        """,
    )
    fun findCandidatesForDeliveryReminder(): List<Order>

    /** Seller dashboard trend cards — see StoreService.getStats. Sums 0 (via coalesce) rather than null when nothing matches, so callers never null-check. */
    @Query(
        """
        select coalesce(sum(o.subtotal), 0) from Order o
        where o.store.id = :storeId
          and o.paymentStatus = com.storepilot.backend.order.PaymentStatus.PAID
          and o.status <> com.storepilot.backend.order.OrderStatus.CANCELLED
          and o.createdAt >= :from and o.createdAt < :to
        """,
    )
    fun sumSubtotalForPaidOrders(storeId: UUID, from: Instant, to: Instant): Int

    @Query(
        """
        select coalesce(sum(o.platformFee), 0) from Order o
        where o.store.id = :storeId
          and o.paymentStatus = com.storepilot.backend.order.PaymentStatus.PAID
          and o.status <> com.storepilot.backend.order.OrderStatus.CANCELLED
          and o.createdAt >= :from and o.createdAt < :to
        """,
    )
    fun sumPlatformFeeForPaidOrders(storeId: UUID, from: Instant, to: Instant): Int

    /**
     * Bank-transfer orders still missing a receipt that are due a reminder
     * email: never-reminded orders older than [firstReminderThreshold], or
     * already-reminded orders whose last reminder was before
     * [repeatThreshold]. Naturally stops returning an order once its receipt
     * is uploaded or it's cancelled — no separate "stop reminding" logic
     * needed elsewhere. See ReceiptReminderJob.
     */
    @Query(
        """
        select o from Order o
        where o.paymentMethod = com.storepilot.backend.order.PaymentMethod.BANK_TRANSFER
          and o.paymentStatus = com.storepilot.backend.order.PaymentStatus.UNPAID
          and o.receiptUrl is null
          and o.status <> com.storepilot.backend.order.OrderStatus.CANCELLED
          and (
            (o.lastReminderSentAt is null and o.createdAt <= :firstReminderThreshold)
            or (o.lastReminderSentAt is not null and o.lastReminderSentAt <= :repeatThreshold)
          )
        """,
    )
    fun findDueForReceiptReminder(
        @Param("firstReminderThreshold") firstReminderThreshold: Instant,
        @Param("repeatThreshold") repeatThreshold: Instant,
    ): List<Order>
}
