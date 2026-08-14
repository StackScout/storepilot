package com.storepilot.backend.booking

import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class WeeklyAvailabilityRuleResponse(
    /** 1 (Monday) .. 7 (Sunday) — java.time.DayOfWeek.getValue(). */
    val dayOfWeek: Int,
    val isOpen: Boolean,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
)

data class WeeklyAvailabilityRuleInput(
    @field:NotNull(message = "dayOfWeek is required")
    val dayOfWeek: Int,
    @field:NotNull(message = "isOpen is required")
    val isOpen: Boolean,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
)

/** PUT /api/stores/{storeId}/availability/weekly-rules — always replaces all 7 rows in one call, simplest upsert shape for a "one settings form" UX. */
data class WeeklyAvailabilityInput(
    @field:Size(min = 7, max = 7, message = "Exactly 7 rules required (one per weekday)")
    @field:Valid
    val rules: List<WeeklyAvailabilityRuleInput>,
    /** Reused as both the booking lead time and the cancellation cutoff — see StoreAvailability.leadTimeMinutes's doc comment. */
    val leadTimeMinutes: Int? = null,
)

data class AvailabilityExceptionResponse(
    val id: java.util.UUID,
    val date: LocalDate,
    val isOpen: Boolean,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
    val note: String?,
)

data class AvailabilityExceptionInput(
    @field:NotNull(message = "date is required")
    val date: LocalDate,
    @field:NotNull(message = "isOpen is required")
    val isOpen: Boolean,
    val openTime: LocalTime?,
    val closeTime: LocalTime?,
    val note: String?,
)

data class SlotResponse(
    val start: Instant,
    val end: Instant,
)

data class DayAvailabilityResponse(
    val date: LocalDate,
    val slots: List<SlotResponse>,
)

data class AvailabilityResponse(
    val leadTimeMinutes: Int,
    val weeklyRules: List<WeeklyAvailabilityRuleResponse>,
    val exceptions: List<AvailabilityExceptionResponse>,
)
