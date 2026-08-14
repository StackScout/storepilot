package com.storepilot.backend.booking

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface StoreAvailabilityRepository : JpaRepository<StoreAvailability, UUID>

interface WeeklyAvailabilityRuleRepository : JpaRepository<WeeklyAvailabilityRule, UUID> {
    fun findByStoreIdOrderByDayOfWeekAsc(storeId: UUID): List<WeeklyAvailabilityRule>

    fun deleteByStoreId(storeId: UUID)
}

interface AvailabilityExceptionRepository : JpaRepository<AvailabilityException, UUID> {
    fun findByStoreIdOrderByDateAsc(storeId: UUID): List<AvailabilityException>

    fun findByStoreIdAndDateBetween(storeId: UUID, from: LocalDate, to: LocalDate): List<AvailabilityException>

    fun findByStoreIdAndDate(storeId: UUID, date: LocalDate): AvailabilityException?
}
