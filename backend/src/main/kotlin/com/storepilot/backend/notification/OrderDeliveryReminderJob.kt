package com.storepilot.backend.notification

import com.storepilot.backend.order.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Nudges the seller to confirm delivery once a shipped order has been out
 * past its expected delivery window (Order.shippedAt + Order
 * .deliveryTimeHours) without being marked DELIVERED yet — most useful for
 * COD orders, where the seller is the one who marks delivery (see
 * OrderService.updateStatus). One-shot, same idempotency shape as every
 * other reminder job here.
 */
@Component
class OrderDeliveryReminderJob(
    private val orderRepository: OrderRepository,
    private val orderNotifier: OrderNotifier,
) {
    private val log = LoggerFactory.getLogger(OrderDeliveryReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val due = orderRepository.findCandidatesForDeliveryReminder().filter { order ->
            val shippedAt = order.shippedAt ?: return@filter false
            now >= shippedAt.plus(order.deliveryTimeHours.toLong(), ChronoUnit.HOURS)
        }

        due.forEach { order ->
            orderNotifier.deliveryDueReminder(order)
            order.deliveryReminderSentAt = now
        }
        if (due.isNotEmpty()) {
            orderRepository.saveAll(due)
            log.info("Sent {} delivery reminder push(es)", due.size)
        }
    }
}
