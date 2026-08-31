package com.storepilot.backend.store

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

@RestController
class StoreStaffController(
    private val storeStaffService: StoreStaffService,
) {
    @PostMapping("/api/stores/{storeId}/staff/invite")
    fun invite(@PathVariable storeId: UUID, @Valid @RequestBody input: StaffInviteInput): ResponseEntity<StoreStaffInviteResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(storeStaffService.invite(storeId, input))

    @GetMapping("/api/stores/{storeId}/staff")
    fun listStaff(@PathVariable storeId: UUID): List<StoreStaffMemberResponse> = storeStaffService.listStaff(storeId)

    @GetMapping("/api/stores/{storeId}/staff/invites")
    fun listPendingInvites(@PathVariable storeId: UUID): List<StoreStaffInviteResponse> = storeStaffService.listPendingInvites(storeId)

    @DeleteMapping("/api/stores/{storeId}/staff/{staffMemberId}")
    fun removeStaff(@PathVariable storeId: UUID, @PathVariable staffMemberId: UUID): ResponseEntity<Void> {
        storeStaffService.removeStaff(storeId, staffMemberId)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/api/stores/{storeId}/staff/invites/{inviteId}")
    fun revokeInvite(@PathVariable storeId: UUID, @PathVariable inviteId: UUID): ResponseEntity<Void> {
        storeStaffService.revokeInvite(storeId, inviteId)
        return ResponseEntity.noContent().build()
    }

    /** Public — lets the accept-invite page render before the invitee is authenticated. */
    @GetMapping("/api/staff/invites/{token}")
    fun getInviteDetails(@PathVariable token: String): StaffInviteDetailsResponse = storeStaffService.getInviteDetails(token)
}
