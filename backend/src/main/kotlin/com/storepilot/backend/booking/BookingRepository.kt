package com.storepilot.backend.booking

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID> {
    /** Unpaged — internal cross-service use (BookingAnalyticsService needs every booking to compute stats; SellerExportService's full data-export bundle). GET /api/stores/{storeId}/bookings uses the paged overloads below. */
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Booking>

    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID, pageable: Pageable): Page<Booking>

    fun findByStoreIdAndStatusOrderByCreatedAtDesc(storeId: UUID, status: BookingStatus, pageable: Pageable): Page<Booking>

    /** Unpaged — internal cross-service use (e.g. BuyerAccountService's account-deletion sweep). GET /api/me/bookings uses the paged overload below. */
    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Booking>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID, pageable: Pageable): Page<Booking>

    /** Close-store precondition check — see StoreService.closeStore. */
    fun existsByStoreIdAndStatusIn(storeId: UUID, statuses: Collection<BookingStatus>): Boolean

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

    /**
     * Candidate pool for SellerBookingReminderJob — every seller offset is
     * different (StoreSettings.sellerBookingReminderMinutesBefore), so
     * unlike findDueForReminder this can't filter to an exact window in
     * SQL; the job itself checks each booking's own store's offset. Capped
     * at maxLookahead (job passes now+30 days) purely to keep this bounded,
     * mirroring AvailabilityService's own 30-day slot-computation window.
     */
    @Query(
        """
        select b from Booking b
        where b.status in (com.storepilot.backend.booking.BookingStatus.PENDING, com.storepilot.backend.booking.BookingStatus.CONFIRMED)
          and b.sellerReminderSentAt is null
          and b.scheduledStart > :now and b.scheduledStart < :maxLookahead
        """,
    )
    fun findCandidatesForSellerReminder(@Param("now") now: Instant, @Param("maxLookahead") maxLookahead: Instant): List<Booking>

    /** GET /api/bookings/recurrence/{groupId} — every occurrence of a recurring series, in chronological order. */
    fun findByRecurrenceGroupIdOrderByScheduledStartAsc(recurrenceGroupId: UUID): List<Booking>

    /**
     * Seller dashboard trend cards — see StoreService.getStats, summed
     * alongside OrderRepository.sumSubtotalForPaidOrders so a bookings-only
     * seller doesn't see $0 revenue despite live business. servicePrice
     * (pre-discount), not total, to mirror Order.subtotal's own
     * pre-discount convention. Sums 0 (via coalesce) rather than null when
     * nothing matches, same as the Order-side query.
     */
    @Query(
        """
        select coalesce(sum(b.servicePrice), 0) from Booking b
        where b.store.id = :storeId
          and b.paymentStatus = com.storepilot.backend.order.PaymentStatus.PAID
          and b.status <> com.storepilot.backend.booking.BookingStatus.CANCELLED
          and b.createdAt >= :from and b.createdAt < :to
        """,
    )
    fun sumServicePriceForPaidBookings(storeId: UUID, from: Instant, to: Instant): Int

    @Query(
        """
        select coalesce(sum(b.platformFee), 0) from Booking b
        where b.store.id = :storeId
          and b.paymentStatus = com.storepilot.backend.order.PaymentStatus.PAID
          and b.status <> com.storepilot.backend.booking.BookingStatus.CANCELLED
          and b.createdAt >= :from and b.createdAt < :to
        """,
    )
    fun sumPlatformFeeForPaidBookings(storeId: UUID, from: Instant, to: Instant): Int
}
