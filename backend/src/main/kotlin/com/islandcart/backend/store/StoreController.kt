package com.islandcart.backend.store

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Matches the endpoints documented in docs/api-contracts.md#stores and #admin. */
@RestController
class StoreController(
    private val storeService: StoreService,
) {
    @GetMapping("/api/stores")
    fun search(
        @RequestParam category: String?,
        @RequestParam query: String?,
        @RequestParam limit: Int?,
    ): List<StoreResponse> = storeService.search(category, query, limit)

    @GetMapping("/api/stores/id/{id}")
    fun getById(@PathVariable id: UUID): StoreResponse = storeService.getById(id)

    @GetMapping("/api/stores/{slug}")
    fun getBySlug(@PathVariable slug: String): StoreResponse = storeService.getBySlug(slug)

    @GetMapping("/api/stores/{storeId}/settings")
    fun getSettings(@PathVariable storeId: UUID): ResponseEntity<StoreSettingsResponse> {
        val settings = storeService.getSettings(storeId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(settings)
    }

    @PatchMapping("/api/stores/{storeId}/settings")
    fun upsertSettings(
        @PathVariable storeId: UUID,
        @RequestBody input: StoreSettingsInput,
    ): StoreSettingsResponse = storeService.upsertSettings(storeId, input)

    @PostMapping("/api/stores")
    fun create(@Valid @RequestBody input: StoreApplicationInput): ResponseEntity<StoreResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(storeService.create(input))

    @GetMapping("/api/admin/stores")
    fun adminList(@RequestParam status: String?): List<StoreResponse> = storeService.adminList(status)

    @PatchMapping("/api/admin/stores/{storeId}/verification")
    fun setVerificationStatus(
        @PathVariable storeId: UUID,
        @Valid @RequestBody input: VerificationDecisionInput,
    ): StoreResponse = storeService.setVerificationStatus(storeId, input)
}
