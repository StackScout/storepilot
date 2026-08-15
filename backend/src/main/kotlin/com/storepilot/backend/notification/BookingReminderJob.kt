package com.storepilot.backend.notification

import com.storepilot.backend.booking.BookingRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Periodically emails buyers whose PENDING/CONFIRMED booking's scheduledStart
 * falls inside the next notifications.booking-reminder-before-hours window.
 * One-shot per booking (Booking.lastReminderSentAt), so a booking that's
 * cancelled or completed before the window is reached just never appears in
 * BookingRepository.findDueForReminder — no separate "stop" branch needed,
 * same shape as ReceiptReminderJob.
 */
@Component
class BookingReminderJob(
    private val bookingRepository: BookingRepository,
    private val bookingNotifier: BookingNotifier,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(BookingReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val windowEnd = now.plus(notificationProperties.bookingReminderBeforeHours, ChronoUnit.HOURS)

        val due = bookingRepository.findDueForReminder(now, windowEnd)
        due.forEach { booking ->
            bookingNotifier.bookingReminder(booking)
            booking.lastReminderSentAt = now
        }
        if (due.isNotEmpty()) {
            bookingRepository.saveAll(due)
            log.info("Sent {} booking reminder email(s)", due.size)
        }
    }
}
