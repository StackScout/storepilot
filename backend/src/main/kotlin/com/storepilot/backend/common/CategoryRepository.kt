package com.storepilot.backend.common

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {
    fun findAllByOrderBySortOrderAscNameAsc(): List<Category>

    fun findAllByActiveTrueOrderBySortOrderAscNameAsc(): List<Category>

    fun findByWireValue(wireValue: String): Category?

    fun existsByWireValue(wireValue: String): Boolean
}

/**
 * Validates [wireValue] against the categories table, mirroring
 * wireValueOf<T>()'s "unknown value -> IllegalArgumentException" contract
 * (mapped to 400 VALIDATION_ERROR by GlobalExceptionHandler) — now backed by
 * an admin-managed table instead of a fixed enum. Returns the same string
 * back (not the Category row) since callers only ever want to persist the
 * wire value onto their own entity's `category` column.
 */
fun CategoryRepository.requireCategory(wireValue: String): String {
    if (!existsByWireValue(wireValue)) {
        throw IllegalArgumentException("Invalid category \"$wireValue\"")
    }
    return wireValue
}
