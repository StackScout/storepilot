package com.storepilot.backend.booking

import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

private const val DEFAULT_WINDOW_DAYS = 30L

@RestController
class AvailabilityController(
    private val availabilityService: AvailabilityService,
) {
    @GetMapping("/api/stores/{storeId}/availability")
    fun get(@PathVariable storeId: UUID): AvailabilityResponse = availabilityService.get(storeId)

    @PutMapping("/api/stores/{storeId}/availability/weekly-rules")
    fun upsertWeeklyRules(
        @PathVariable storeId: UUID,
        @Valid @RequestBody input: WeeklyAvailabilityInput,
    ): AvailabilityResponse = availabilityService.upsertWeeklyRules(storeId, input)

    @PostMapping("/api/stores/{storeId}/availability/exceptions")
    fun createException(
        @PathVariable storeId: UUID,
        @Valid @RequestBody input: AvailabilityExceptionInput,
    ): ResponseEntity<AvailabilityExceptionResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(availabilityService.createException(storeId, input))

    @DeleteMapping("/api/stores/{storeId}/availability/exceptions/{exceptionId}")
    fun deleteException(@PathVariable storeId: UUID, @PathVariable exceptionId: UUID): ResponseEntity<Void> {
        availabilityService.deleteException(storeId, exceptionId)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/api/stores/{storeId}/bookable-services/{serviceId}/availability")
    fun computeSlots(
        @PathVariable storeId: UUID,
        @PathVariable serviceId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): List<DayAvailabilityResponse> {
        val resolvedFrom = from ?: LocalDate.now()
        val resolvedTo = to ?: resolvedFrom.plusDays(DEFAULT_WINDOW_DAYS)
        return availabilityService.computeSlots(storeId, serviceId, resolvedFrom, resolvedTo)
    }

    /** [storeId] unused (service ownership is resolved via the service itself) but kept in the path for consistency with every other bookable-services route. */
    @GetMapping("/api/stores/{storeId}/bookable-services/{serviceId}/availability-override")
    fun getServiceOverride(@PathVariable storeId: UUID, @PathVariable serviceId: UUID): ServiceAvailabilityOverrideResponse =
        availabilityService.getServiceOverride(serviceId)

    @PutMapping("/api/stores/{storeId}/bookable-services/{serviceId}/availability-override")
    fun upsertServiceOverride(
        @PathVariable storeId: UUID,
        @PathVariable serviceId: UUID,
        @Valid @RequestBody input: ServiceAvailabilityOverrideInput,
    ): ServiceAvailabilityOverrideResponse = availabilityService.upsertServiceOverride(serviceId, input)

    /** Reverts to inheriting the store's default weekly template — see AvailabilityService.disableServiceOverride. */
    @DeleteMapping("/api/stores/{storeId}/bookable-services/{serviceId}/availability-override")
    fun disableServiceOverride(@PathVariable storeId: UUID, @PathVariable serviceId: UUID): ResponseEntity<Void> {
        availabilityService.disableServiceOverride(serviceId)
        return ResponseEntity.noContent().build()
    }
}
