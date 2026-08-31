package com.storepilot.backend.store

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class StaffInviteInput(
    @field:NotBlank val name: String,
    @field:NotBlank @field:Email val email: String,
)

data class StoreStaffInviteResponse(
    val id: UUID,
    val email: String,
    val name: String,
    val status: String,
    val invitedAt: Instant,
    val expiresAt: Instant,
)

data class StoreStaffMemberResponse(
    val id: UUID,
    val sellerId: UUID,
    val name: String,
    val email: String,
    val joinedAt: Instant,
)

/** Returned by the public "what am I accepting" lookup — never exposes the store id itself, just enough to render the invite screen. */
data class StaffInviteDetailsResponse(
    val storeName: String,
    val email: String,
    val name: String,
    val expiresAt: Instant,
)

data class AcceptStaffInviteInput(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
    val name: String? = null,
)
