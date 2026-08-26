package com.storepilot.backend.notification

import com.storepilot.backend.order.Order
import com.storepilot.backend.order.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * "Ship this soon" and "this is overdue" seller pushes for orders that
 * haven't shipped yet — deadline is Order.createdAt + Order
 * .fulfillmentTimeHours (resolved/snapshotted at checkout, see Order.kt's
 * doc comment). Both reminders are one-shot (their own *ReminderSentAt
 * column), so an order that's still not shipped gets exactly one due-soon
 * push and, later, exactly one overdue push — never a repeating nag.
 */
@Component
class OrderFulfillmentReminderJob(
    private val orderRepository: OrderRepository,
    private val orderNotifier: OrderNotifier,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(OrderFulfillmentReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val candidates = orderRepository.findCandidatesForFulfillmentReminder()

        val dueSoon = mutableListOf<Order>()
        val overdue = mutableListOf<Order>()

        candidates.forEach { order ->
            val createdAt = order.createdAt ?: return@forEach
            val deadline = createdAt.plus(order.fulfillmentTimeHours.toLong(), ChronoUnit.HOURS)
            if (order.fulfillmentOverdueReminderSentAt == null && now >= deadline) {
                overdue.add(order)
            } else if (order.fulfillmentReminderSentAt == null && now >= deadline.minus(notificationProperties.fulfillmentDueSoonLeadHours, ChronoUnit.HOURS)) {
                dueSoon.add(order)
            }
        }

        dueSoon.forEach { orderNotifier.fulfillmentDueSoon(it); it.fulfillmentReminderSentAt = now }
        overdue.forEach { orderNotifier.fulfillmentOverdue(it); it.fulfillmentOverdueReminderSentAt = now }

        if (dueSoon.isNotEmpty() || overdue.isNotEmpty()) {
            orderRepository.saveAll(dueSoon + overdue)
            log.info("Sent {} fulfillment due-soon and {} overdue push(es)", dueSoon.size, overdue.size)
        }
    }
}
