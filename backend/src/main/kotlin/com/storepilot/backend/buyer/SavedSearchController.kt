package com.storepilot.backend.buyer

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** The authenticated buyer's saved searches — falls under the existing /api/me prefix's ROLE_BUYER gate in SecurityConfig, no new matcher needed. */
@RestController
class SavedSearchController(
    private val savedSearchService: SavedSearchService,
) {
    @GetMapping("/api/me/saved-searches")
    fun list(): List<SavedSearchResponse> = savedSearchService.list()

    @PostMapping("/api/me/saved-searches")
    fun create(@Valid @RequestBody input: SavedSearchInput): ResponseEntity<SavedSearchResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(savedSearchService.create(input))

    @DeleteMapping("/api/me/saved-searches/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        savedSearchService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
