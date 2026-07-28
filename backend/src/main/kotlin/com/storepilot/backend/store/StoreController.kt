package com.storepilot.backend.store

import com.storepilot.backend.common.PageResponse
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
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
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
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") size: Int,
    ): PageResponse<StoreResponse> = storeService.search(category, query, page, size)

    @GetMapping("/api/stores/id/{id}")
    fun getById(@PathVariable id: UUID): StoreResponse = storeService.getById(id)

    @GetMapping("/api/stores/{slug}")
    fun getBySlug(@PathVariable slug: String): StoreResponse = storeService.getBySlug(slug)

    @GetMapping("/api/me/store")
    fun getMyStore(): ResponseEntity<StoreResponse> {
        val store = storeService.getMyStore() ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(store)
    }

    @GetMapping("/api/stores/{storeId}/settings")
    fun getSettings(@PathVariable storeId: UUID): ResponseEntity<StoreSettingsResponse> {
        val settings = storeService.getSettings(storeId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(settings)
    }

    @PatchMapping("/api/stores/{storeId}/settings")
    fun upsertSettings(
        @PathVariable storeId: UUID,
        @RequestBody input: StoreSettingsInput,
    ): StoreSettingsResponse = storeService.updateSettingsAsSeller(storeId, input)

    @PatchMapping("/api/stores/{storeId}/profile")
    fun updateProfile(
        @PathVariable storeId: UUID,
        @RequestBody input: StoreProfileInput,
    ): StoreResponse = storeService.updateProfileAsSeller(storeId, input)

    @PostMapping("/api/stores/{storeId}/driver-licence-document", consumes = ["multipart/form-data"])
    fun uploadDriverLicenceDocument(
        @PathVariable storeId: UUID,
        @RequestPart file: MultipartFile,
    ): StoreSettingsResponse = storeService.uploadDriverLicenceDocument(storeId, file)

    @PostMapping("/api/stores/{storeId}/abn-document", consumes = ["multipart/form-data"])
    fun uploadAbnDocument(
        @PathVariable storeId: UUID,
        @RequestPart file: MultipartFile,
    ): StoreSettingsResponse = storeService.uploadAbnDocument(storeId, file)

    @PostMapping("/api/stores/{storeId}/nic-document", consumes = ["multipart/form-data"])
    fun uploadNicDocument(
        @PathVariable storeId: UUID,
        @RequestPart file: MultipartFile,
    ): StoreSettingsResponse = storeService.uploadNicDocument(storeId, file)

    @PostMapping("/api/stores/{storeId}/business-reg-document", consumes = ["multipart/form-data"])
    fun uploadBusinessRegDocument(
        @PathVariable storeId: UUID,
        @RequestPart file: MultipartFile,
    ): StoreSettingsResponse = storeService.uploadBusinessRegDocument(storeId, file)

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
