package com.storepilot.backend.admin

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class InviteAdminInput(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,
)

/** What invite() returns — deliberately not an Admin/AdminSummaryResponse, since no Admin row exists yet (JIT-created on the invitee's first login, see Admin.kt's doc comment). */
data class InviteAdminResult(
    val email: String,
    val name: String,
)

/** One row per Cognito user in the `admin` group — sourced from Cognito directly (ListUsersInGroup), not the local Admin table, so an invited admin who hasn't logged in yet still shows up. */
data class AdminSummaryResponse(
    val email: String,
    val name: String,
    val invitedAt: Instant,
)
