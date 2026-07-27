package com.islandcart.backend.common.security

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterInput(
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,
    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters")
    val password: String,
)

data class LoginInput(
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:NotBlank(message = "Password is required")
    val password: String,
)

/**
 * What the frontend gets back to know "am I signed in, and as what" — the
 * access/refresh tokens themselves are httpOnly, so JS can never read them
 * directly; this response is the only way the client learns its own auth
 * state after login/register/refresh, or via GET /api/auth/session.
 */
data class AuthSessionResponse(
    val signedIn: Boolean,
    val role: String? = null,
    val email: String? = null,
    val name: String? = null,
)
