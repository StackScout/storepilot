package com.storepilot.backend.common.security

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
    // "buyer" or "seller" — determines which Cognito group (if any) is
    // granted immediately. Buyer and seller are mutually exclusive
    // identities (see AuthController.register()'s doc comment): a "seller"
    // registration gets no group at all until onboarding grants "seller".
    @field:NotBlank(message = "Account type is required")
    val accountType: String,
)

data class LoginInput(
    @field:NotBlank(message = "Email is required")
    val email: String,
    @field:NotBlank(message = "Password is required")
    val password: String,
)

/** POST /api/auth/refresh's optional body — only used when the caller has no refresh-token cookie (i.e. a mobile client), which sends back the refreshToken it captured from login/mfaChallenge instead. Ignored when the cookie is present. */
data class RefreshInput(
    val refreshToken: String? = null,
)

/** What register() returns now that it no longer signs the caller in — see AuthController.register()'s doc comment. */
data class RegisterResponse(
    val email: String,
    val name: String,
)

data class VerifyEmailInput(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,
    @field:NotBlank(message = "Code is required")
    val code: String,
)

data class ResendVerificationInput(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,
)

/**
 * What the frontend gets back to know "am I signed in, and as what" — the
 * access/refresh tokens themselves are httpOnly, so JS can never read them
 * directly; this response is the only way the client learns its own auth
 * state after login/register/refresh, or via GET /api/auth/session.
 *
 * `mfaRequired`/`mfaSession` are only populated when login() hits a
 * SOFTWARE_TOKEN_MFA challenge instead of completing — `signedIn` stays
 * false in that case, since no cookies have been set yet. The frontend
 * must prompt for a TOTP code and POST it (with `mfaSession`) to
 * /api/auth/mfa/challenge to actually complete sign-in.
 *
 * `accessToken`/`refreshToken` are only populated by completeLogin() (i.e.
 * login()/mfaChallenge() on success) and refresh() — never by session()
 * below, which just reflects state from the caller's existing credential.
 * The web app has no use for these (it authenticates via the httpOnly
 * cookies CookieBearerTokenResolver reads) and ignores them; a mobile
 * client with no cookie jar captures them here and sends them back as an
 * `Authorization: Bearer` header — see CookieBearerTokenResolver's doc
 * comment on why both transports are supported.
 */
data class AuthSessionResponse(
    val signedIn: Boolean,
    val role: String? = null,
    val email: String? = null,
    val name: String? = null,
    val mfaRequired: Boolean = false,
    val mfaSession: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

/** Completes a login that returned mfaRequired=true — see AuthSessionResponse's doc comment. */
data class MfaChallengeInput(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Must be a valid email")
    val email: String,
    @field:NotBlank(message = "Session is required")
    val session: String,
    @field:NotBlank(message = "Code is required")
    val code: String,
)

/** secret is the raw base32 TOTP secret (shown as a manual-entry fallback); otpauthUri is what the frontend renders as a QR code. Neither is persisted server-side — Cognito holds the enrolled secret once verify() succeeds. */
data class MfaSetupResponse(
    val secret: String,
    val otpauthUri: String,
)

data class MfaVerifyInput(
    @field:NotBlank(message = "Code is required")
    val code: String,
)

data class MfaStatusResponse(
    val enabled: Boolean,
)
