package com.storepilot.backend.buyer

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class SavedSearchInput(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Query is required")
    val queryString: String,
)

data class SavedSearchResponse(
    val id: UUID,
    val name: String,
    val queryString: String,
    val createdAt: Instant,
)

fun SavedSearch.toResponse() = SavedSearchResponse(
    id = requireNotNull(id),
    name = name,
    queryString = queryString,
    createdAt = requireNotNull(createdAt),
)
