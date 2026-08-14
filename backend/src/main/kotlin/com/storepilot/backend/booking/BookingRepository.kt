package com.storepilot.backend.booking

import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface BookingRepository : JpaRepository<Booking, UUID> {
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<Booking>

    fun findByBuyerIdOrderByCreatedAtDesc(buyerId: UUID): List<Booking>

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
}
