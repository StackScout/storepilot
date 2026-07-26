package com.islandcart.backend.order

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Order>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Order>

    fun findByOrderNumberIgnoreCase(orderNumber: String): Order?

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
        where o.paymentMethod = com.islandcart.backend.order.PaymentMethod.BANK_TRANSFER
          and o.paymentStatus = com.islandcart.backend.order.PaymentStatus.UNPAID
          and o.receiptUrl is null
          and o.status <> com.islandcart.backend.order.OrderStatus.CANCELLED
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
