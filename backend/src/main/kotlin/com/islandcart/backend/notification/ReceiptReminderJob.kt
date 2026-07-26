package com.islandcart.backend.notification

import com.islandcart.backend.order.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Periodically emails buyers who placed a bank-transfer order but haven't
 * uploaded a receipt yet. Keeps re-sending on notifications.reminder-interval-hours
 * until the order either gets a receipt or is cancelled — both of which drop
 * it out of OrderRepository.findDueForReceiptReminder's result set, so there's
 * no separate "stop" branch here.
 */
@Component
class ReceiptReminderJob(
    private val orderRepository: OrderRepository,
    private val orderNotifier: OrderNotifier,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(ReceiptReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val firstReminderThreshold = now.minus(notificationProperties.firstReminderAfterHours, ChronoUnit.HOURS)
        val repeatThreshold = now.minus(notificationProperties.reminderIntervalHours, ChronoUnit.HOURS)

        val due = orderRepository.findDueForReceiptReminder(firstReminderThreshold, repeatThreshold)
        due.forEach { order ->
            orderNotifier.receiptReminder(order)
            order.lastReminderSentAt = now
        }
        if (due.isNotEmpty()) {
            orderRepository.saveAll(due)
            log.info("Sent {} receipt reminder email(s)", due.size)
        }
    }
}
