package com.storepilot.backend.notification

import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.store.StoreSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Seller-facing counterpart to BookingReminderJob (which reminds the
 * buyer). Unlike that job, the offset before scheduledStart isn't one
 * global constant — it's each store's own StoreSettings
 * .sellerBookingReminderMinutesBefore — so this pulls a bounded candidate
 * pool (see BookingRepository.findCandidatesForSellerReminder) and checks
 * each booking's own threshold in Kotlin rather than in the query.
 */
@Component
class SellerBookingReminderJob(
    private val bookingRepository: BookingRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val bookingNotifier: BookingNotifier,
) {
    private val log = LoggerFactory.getLogger(SellerBookingReminderJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val maxLookahead = now.plus(30, ChronoUnit.DAYS)

        val candidates = bookingRepository.findCandidatesForSellerReminder(now, maxLookahead)
        val due = candidates.filter { booking ->
            val storeId = booking.store.id ?: return@filter false
            val minutesBefore = storeSettingsRepository.findById(storeId).map { it.sellerBookingReminderMinutesBefore }.orElse(60)
            now >= booking.scheduledStart.minus(minutesBefore.toLong(), ChronoUnit.MINUTES)
        }

        due.forEach { booking ->
            bookingNotifier.sellerBookingReminder(booking)
            booking.sellerReminderSentAt = now
        }
        if (due.isNotEmpty()) {
            bookingRepository.saveAll(due)
            log.info("Sent {} seller booking reminder push(es)", due.size)
        }
    }
}
