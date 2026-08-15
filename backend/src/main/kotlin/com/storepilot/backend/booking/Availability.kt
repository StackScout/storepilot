package com.storepilot.backend.booking

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.store.Store
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * One row per store — the lead-time policy shared by every bookable
 * service, alongside the store's weekly template (WeeklyAvailabilityRule)
 * and date overrides (AvailabilityException). Always store-level, even for
 * a service with its own weekly-hours override (ServiceWeeklyAvailabilityRule)
 * — lead time/cancellation cutoff isn't overridable per service in v1.
 * Sparse 1:1 child of Store, same @MapsId shape as StoreSettings — not
 * every store has one until it enables bookings.
 */
@Entity
@Table(name = "store_availability")
class StoreAvailability(
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "store_id")
    var store: Store,
    /**
     * Minutes of lead time required before a slot can be booked, and reused
     * as the cancellation cutoff (can't cancel within this window either) —
     * one number for both directions, not independently configurable in v1.
     */
    @Column(name = "lead_time_minutes", nullable = false)
    var leadTimeMinutes: Int = 120,
) : BaseEntity()

/**
 * A store's recurring weekly open-hours template — one row per weekday,
 * exactly 7 per store once configured. AvailabilityException rows for a
 * specific date take precedence over this when computing slots; a service
 * with hasCustomAvailability = true uses ServiceWeeklyAvailabilityRule
 * instead of this for its own weekly resolution — see
 * AvailabilityService.computeSlots.
 */
@Entity
@Table(
    name = "weekly_availability_rules",
    uniqueConstraints = [UniqueConstraint(columnNames = ["store_id", "day_of_week"])],
)
class WeeklyAvailabilityRule(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    /**
     * java.time.DayOfWeek — MONDAY(1) .. SUNDAY(7), stored via getValue()
     * through DayOfWeekIntConverter. Without an explicit converter, Hibernate
     * defaults to persisting enums by ordinal (0-6), which both mismatches
     * the migration's documented 1-7 convention and throws
     * ArrayIndexOutOfBoundsException on read for Sunday (ordinal 6 read back
     * as index 7 is fine, but a 1-7-convention value of 7 read as an ordinal
     * index is not — 7 is out of bounds for the 7-constant DayOfWeek array).
     */
    @Convert(converter = DayOfWeekIntConverter::class)
    @Column(name = "day_of_week", nullable = false)
    var dayOfWeek: DayOfWeek,
    @Column(name = "is_open", nullable = false)
    var isOpen: Boolean,
    @Column(name = "open_time")
    var openTime: LocalTime? = null,
    @Column(name = "close_time")
    var closeTime: LocalTime? = null,
) : BaseEntity()

/**
 * A date-specific override to the weekly template — either a closure
 * (isOpen = false, e.g. a public holiday) or a special one-off opening
 * (isOpen = true, e.g. a normally-closed Sunday opened specially). Wins
 * outright over WeeklyAvailabilityRule for that date when computing slots.
 */
@Entity
@Table(
    name = "availability_exceptions",
    uniqueConstraints = [UniqueConstraint(columnNames = ["store_id", "exception_date"])],
)
class AvailabilityException(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(name = "exception_date", nullable = false)
    var date: LocalDate,
    @Column(name = "is_open", nullable = false)
    var isOpen: Boolean,
    @Column(name = "open_time")
    var openTime: LocalTime? = null,
    @Column(name = "close_time")
    var closeTime: LocalTime? = null,
    /** Shown to buyers on the booking page, e.g. "Closed for Vesak". */
    var note: String? = null,
) : BaseEntity()

/**
 * A per-service override of the store's weekly template — only present for
 * a service whose BookableService.hasCustomAvailability is true, in which
 * case computeSlots uses these 7 rows instead of the store's
 * WeeklyAvailabilityRule for that service. AvailabilityException stays
 * store-only (not mirrored here) — a holiday closure applies to every
 * service regardless of override, see BookableService.hasCustomAvailability's
 * doc comment.
 */
@Entity
@Table(
    name = "service_weekly_availability_rules",
    uniqueConstraints = [UniqueConstraint(columnNames = ["service_id", "day_of_week"])],
)
class ServiceWeeklyAvailabilityRule(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    var service: BookableService,
    @Convert(converter = DayOfWeekIntConverter::class)
    @Column(name = "day_of_week", nullable = false)
    var dayOfWeek: DayOfWeek,
    @Column(name = "is_open", nullable = false)
    var isOpen: Boolean,
    @Column(name = "open_time")
    var openTime: LocalTime? = null,
    @Column(name = "close_time")
    var closeTime: LocalTime? = null,
) : BaseEntity()

@Converter
class DayOfWeekIntConverter : AttributeConverter<DayOfWeek, Int> {
    override fun convertToDatabaseColumn(attribute: DayOfWeek?): Int? = attribute?.value
    override fun convertToEntityAttribute(dbData: Int?): DayOfWeek? = dbData?.let { DayOfWeek.of(it) }
}
