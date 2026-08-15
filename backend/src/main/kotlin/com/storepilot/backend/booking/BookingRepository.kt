package com.storepilot.backend.booking

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID> {
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Booking>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Booking>

    /** Verified-purchase gate for a store review — see ReviewService.createStoreReview. */
    fun existsByBuyerIdAndStoreIdAndStatus(buyerId: UUID, storeId: UUID, status: BookingStatus): Boolean

    /** GET /api/bookings/lookup — mirrors OrderRepository.findByOrderNumberIgnoreCase, suffix-matched against buyerPhone in BookingService. */
    fun findByBookingNumberIgnoreCase(bookingNumber: String): Booking?

    /** Delete-guard for BookableServiceService.delete — see BookableService's doc comment on why deletion is refused while a live obligation exists. */
    fun existsByServiceIdAndStatusNotIn(serviceId: UUID, statuses: Collection<BookingStatus>): Boolean

    /**
     * The slot-overlap check's hot path — see AvailabilityService.computeSlots
     * and BookingService.createBooking's race-condition re-validation. Backed
     * by idx_bookings_service_scheduled (see V13__bookings.sql).
     */
    fun findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(
        serviceId: UUID,
        excludedStatuses: Collection<BookingStatus>,
        beforeEnd: Instant,
        afterStart: Instant,
    ): List<Booking>

    /**
     * Upcoming, not-yet-reminded bookings whose scheduledStart falls inside
     * the reminder window (now..now+bookingReminderBeforeHours) — one-shot,
     * see Booking.lastReminderSentAt's doc comment. See BookingReminderJob.
     */
    @Query(
        """
        select b from Booking b
        where b.status in (com.storepilot.backend.booking.BookingStatus.PENDING, com.storepilot.backend.booking.BookingStatus.CONFIRMED)
          and b.lastReminderSentAt is null
          and b.scheduledStart >= :windowStart and b.scheduledStart < :windowEnd
        """,
    )
    fun findDueForReminder(@Param("windowStart") windowStart: Instant, @Param("windowEnd") windowEnd: Instant): List<Booking>

    /** GET /api/bookings/recurrence/{groupId} — every occurrence of a recurring series, in chronological order. */
    fun findByRecurrenceGroupIdOrderByScheduledStartAsc(recurrenceGroupId: UUID): List<Booking>
}
