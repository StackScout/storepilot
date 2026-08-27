package com.storepilot.backend.store

import com.storepilot.backend.common.PageResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@RestController
class StoreVerificationChangeRequestController(
    private val changeRequestService: StoreVerificationChangeRequestService,
) {
    @GetMapping("/api/stores/{storeId}/verification-change-requests/current")
    fun current(@PathVariable storeId: UUID): ResponseEntity<StoreVerificationChangeRequestResponse> {
        val request = changeRequestService.current(storeId) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(request)
    }

    @PostMapping("/api/stores/{storeId}/verification-change-requests", consumes = ["multipart/form-data"])
    fun submit(
        @PathVariable storeId: UUID,
        @Valid @RequestPart("data") input: VerificationChangeRequestInput,
        @RequestPart(value = "driverLicenceDocument", required = false) driverLicenceDocument: MultipartFile?,
        @RequestPart(value = "abnDocument", required = false) abnDocument: MultipartFile?,
        @RequestPart(value = "nicDocument", required = false) nicDocument: MultipartFile?,
        @RequestPart(value = "businessRegDocument", required = false) businessRegDocument: MultipartFile?,
    ): StoreVerificationChangeRequestResponse =
        changeRequestService.submit(storeId, input, driverLicenceDocument, abnDocument, nicDocument, businessRegDocument)

    // --- Admin — gated by SecurityConfig's hasRole("ADMIN") on /api/admin/** ---

    @GetMapping("/api/admin/verification-change-requests")
    fun adminList(
        @RequestParam status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<StoreVerificationChangeRequestResponse> = changeRequestService.adminList(status, page, size)

    @PostMapping("/api/admin/verification-change-requests/{id}/approve")
    fun adminApprove(@PathVariable id: UUID): StoreSettingsResponse = changeRequestService.adminApprove(id)

    @PostMapping("/api/admin/verification-change-requests/{id}/reject")
    fun adminReject(
        @PathVariable id: UUID,
        @RequestBody input: VerificationChangeRequestReviewInput,
    ): StoreVerificationChangeRequestResponse = changeRequestService.adminReject(id, input)
}
