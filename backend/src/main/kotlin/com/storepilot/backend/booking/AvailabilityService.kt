package com.storepilot.backend.booking

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Weekly-template-plus-exceptions availability, computed on read — no
 * slot-materialization job, no staleness. See docs/features/bookings.md for
 * the full design and the deliberate v1 scope decisions referenced below
 * (store-level not per-service schedules, independent per-service capacity).
 */
@Service
@Transactional(readOnly = true)
class AvailabilityService(
    private val storeAvailabilityRepository: StoreAvailabilityRepository,
    private val weeklyAvailabilityRuleRepository: WeeklyAvailabilityRuleRepository,
    private val availabilityExceptionRepository: AvailabilityExceptionRepository,
    private val bookableServiceRepository: BookableServiceRepository,
    private val bookingRepository: BookingRepository,
    private val storeRepository: StoreRepository,
    private val currentActor: CurrentActor,
    private val platformConfigService: PlatformConfigService,
) {
    private val zoneId: ZoneId get() = ZoneId.of(platformConfigService.current().timezone)

    fun get(storeId: UUID): AvailabilityResponse {
        val leadTimeMinutes = storeAvailabilityRepository.findById(storeId).map { it.leadTimeMinutes }.orElse(120)
        return AvailabilityResponse(
            leadTimeMinutes = leadTimeMinutes,
            weeklyRules = weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId).map { it.toResponse() },
            exceptions = availabilityExceptionRepository.findByStoreIdOrderByDateAsc(storeId).map { it.toResponse() },
        )
    }

    /** Replaces all 7 weekly rows and the lead-time policy in one call — see WeeklyAvailabilityInput's doc comment. */
    @Transactional
    fun upsertWeeklyRules(storeId: UUID, input: WeeklyAvailabilityInput): AvailabilityResponse {
        val store = requireStore(storeId)
        requireOwnership(store)
        val dayNumbers = input.rules.map { it.dayOfWeek }.toSet()
        require(dayNumbers == (1..7).toSet()) { "Rules must cover each weekday exactly once (1=Monday..7=Sunday)" }
        input.rules.forEach {
            require(!it.isOpen || (it.openTime != null && it.closeTime != null && it.openTime < it.closeTime)) {
                "An open day needs a valid openTime before closeTime"
            }
        }

        weeklyAvailabilityRuleRepository.deleteByStoreId(storeId)
        weeklyAvailabilityRuleRepository.saveAll(
            input.rules.map {
                WeeklyAvailabilityRule(
                    store = store,
                    dayOfWeek = java.time.DayOfWeek.of(it.dayOfWeek),
                    isOpen = it.isOpen,
                    openTime = it.openTime,
                    closeTime = it.closeTime,
                )
            },
        )

        val availability = storeAvailabilityRepository.findById(storeId).orElseGet { StoreAvailability(store = store) }
        input.leadTimeMinutes?.let { availability.leadTimeMinutes = it }
        storeAvailabilityRepository.save(availability)

        return get(storeId)
    }

    @Transactional
    fun createException(storeId: UUID, input: AvailabilityExceptionInput): AvailabilityExceptionResponse {
        val store = requireStore(storeId)
        requireOwnership(store)
        require(!input.isOpen || (input.openTime != null && input.closeTime != null && input.openTime < input.closeTime)) {
            "A special opening needs a valid openTime before closeTime"
        }
        val existing = availabilityExceptionRepository.findByStoreIdAndDate(storeId, input.date)
        val exception = existing ?: AvailabilityException(store = store, date = input.date, isOpen = input.isOpen)
        exception.isOpen = input.isOpen
        exception.openTime = input.openTime
        exception.closeTime = input.closeTime
        exception.note = input.note
        return availabilityExceptionRepository.save(exception).toResponse()
    }

    @Transactional
    fun deleteException(storeId: UUID, exceptionId: UUID) {
        val store = requireStore(storeId)
        requireOwnership(store)
        val exception = availabilityExceptionRepository.findById(exceptionId)
            .orElseThrow { NotFoundException("Availability exception $exceptionId not found") }
        require(exception.store.id == store.id) { "Exception $exceptionId does not belong to store $storeId" }
        availabilityExceptionRepository.delete(exception)
    }

    /**
     * Chunks the resolved open window for each date in [from, to] into
     * `duration + buffer`-sized candidate slots, drops anything inside the
     * lead-time cutoff, and drops anything overlapping an existing
     * non-cancelled booking of *this same service* — cross-service
     * double-booking is allowed by design (independent per-service
     * capacity), see docs/features/bookings.md.
     */
    fun computeSlots(storeId: UUID, serviceId: UUID, from: LocalDate, to: LocalDate): List<DayAvailabilityResponse> {
        val service = bookableServiceRepository.findById(serviceId)
            .orElseThrow { NotFoundException("Service $serviceId not found") }
        require(service.store.id == storeId) { "Service $serviceId does not belong to store $storeId" }

        val leadTimeMinutes = storeAvailabilityRepository.findById(storeId).map { it.leadTimeMinutes }.orElse(120)
        val earliestStart = Instant.now().plusSeconds(leadTimeMinutes * 60L)
        val weeklyRulesByDay = weeklyAvailabilityRuleRepository.findByStoreIdOrderByDayOfWeekAsc(storeId)
            .associateBy { it.dayOfWeek }
        val exceptionsByDate = availabilityExceptionRepository.findByStoreIdAndDateBetween(storeId, from, to)
            .associateBy { it.date }
        val excludedStatuses = setOf(BookingStatus.CANCELLED, BookingStatus.NO_SHOW)
        val existingBookings = bookingRepository.findByServiceIdAndStatusNotInAndScheduledStartLessThanAndScheduledEndGreaterThan(
            serviceId,
            excludedStatuses,
            to.plusDays(1).atStartOfDay(zoneId).toInstant(),
            from.atStartOfDay(zoneId).toInstant(),
        )

        val stepMinutes = service.durationMinutes + service.bufferMinutes
        val days = mutableListOf<DayAvailabilityResponse>()
        var date = from
        while (!date.isAfter(to)) {
            val (isOpen, openTime, closeTime) = exceptionsByDate[date]?.let { Triple(it.isOpen, it.openTime, it.closeTime) }
                ?: weeklyRulesByDay[date.dayOfWeek]?.let { Triple(it.isOpen, it.openTime, it.closeTime) }
                ?: Triple(false, null, null)

            val slots = mutableListOf<SlotResponse>()
            if (isOpen && openTime != null && closeTime != null) {
                val windowStart: LocalTime = openTime
                val windowEnd: LocalTime = closeTime
                var slotStart = windowStart
                while (true) {
                    val slotEnd = slotStart.plusMinutes(service.durationMinutes.toLong())
                    if (slotEnd > windowEnd) break
                    val startInstant = date.atTime(slotStart).atZone(zoneId).toInstant()
                    val endInstant = date.atTime(slotEnd).atZone(zoneId).toInstant()
                    val withinLeadTime = startInstant >= earliestStart
                    val overlapsExisting = existingBookings.any { it.scheduledStart < endInstant && it.scheduledEnd > startInstant }
                    if (withinLeadTime && !overlapsExisting) {
                        slots.add(SlotResponse(startInstant, endInstant))
                    }
                    slotStart = slotStart.plusMinutes(stepMinutes.toLong())
                    if (slotStart >= windowEnd) break
                }
            }
            days.add(DayAvailabilityResponse(date, slots))
            date = date.plusDays(1)
        }
        return days
    }

    private fun requireStore(storeId: UUID): Store =
        storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }

    private fun requireOwnership(store: Store) {
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store ${store.id}")
    }
}

private fun WeeklyAvailabilityRule.toResponse() = WeeklyAvailabilityRuleResponse(
    dayOfWeek = dayOfWeek.value,
    isOpen = isOpen,
    openTime = openTime,
    closeTime = closeTime,
)

private fun AvailabilityException.toResponse() = AvailabilityExceptionResponse(
    id = requireNotNull(id),
    date = date,
    isOpen = isOpen,
    openTime = openTime,
    closeTime = closeTime,
    note = note,
)
