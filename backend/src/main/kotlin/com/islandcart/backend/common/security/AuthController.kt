package com.islandcart.backend.common.security

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.UnauthenticatedException
import com.islandcart.backend.notification.NotificationProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseCookie
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.security.core.context.SecurityContextHolder
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

private const val REFRESH_TOKEN_MAX_AGE_SECONDS = 60L * 60 * 24 * 30 // matches Cognito's default refresh token validity

/**
 * The only thing in the app that talks to Cognito directly on the buyer/
 * seller/admin's behalf. Uses the Admin* Cognito APIs
 * (AdminCreateUser/AdminInitiateAuth/...) for the direct email/password
 * flow — exactly what the EC2 instance role's Cognito permissions are
 * scoped for — plus the Hosted-UI OAuth authorization-code flow (a
 * separate, confidential app client, see CognitoProperties) for Google
 * sign-in, which the frontend never talks to Cognito for either: it only
 * ever links to googleStart() below.
 *
 * Registration skips Cognito's email-verification-code flow deliberately
 * (AdminCreateUser + MessageAction=SUPPRESS + AdminSetUserPassword with
 * permanent=true creates an already-usable account immediately) — a
 * pragmatic MVP trade-off, not a security requirement being ignored. Adding
 * real email verification later is a frontend (confirmation-code UI) +
 * backend (ConfirmSignUp call) addition on top of this, not a rework.
 */
@RestController
class AuthController(
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
    private val currentActor: CurrentActor,
    private val notificationProperties: NotificationProperties,
    private val jwtDecoder: JwtDecoder,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)
    private val restClient = RestClient.create()

    @PostMapping("/api/auth/register")
    fun register(
        @Valid @RequestBody input: RegisterInput,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthSessionResponse {
        try {
            cognitoClient.adminCreateUser(
                AdminCreateUserRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .username(input.email)
                    .userAttributes(
                        AttributeType.builder().name("email").value(input.email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(input.name).build(),
                    )
                    .messageAction(MessageActionType.SUPPRESS)
                    .build(),
            )
        } catch (e: UsernameExistsException) {
            throw ConflictException("An account with this email already exists")
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException(e.message ?: "Password doesn't meet requirements")
        }

        cognitoClient.adminSetUserPassword(
            AdminSetUserPasswordRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(input.email)
                .password(input.password)
                .permanent(true)
                .build(),
        )

        addToGroupWithRetry(input.email, "buyer")

        val authResult = adminInitiateAuth(input.email, input.password)
        setAuthCookies(response, request.isSecure, authResult)
        return AuthSessionResponse(signedIn = true, role = "buyer", email = input.email, name = input.name)
    }

    @PostMapping("/api/auth/login")
    fun login(
        @Valid @RequestBody input: LoginInput,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthSessionResponse {
        val authResult = adminInitiateAuth(input.email, input.password)
        setAuthCookies(response, request.isSecure, authResult)

        val attributes = cognitoClient.adminGetUser(
            AdminGetUserRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(input.email)
                .build(),
        ).userAttributes().associate { it.name() to it.value() }
        val groups = cognitoClient.adminListGroupsForUser(
            AdminListGroupsForUserRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(input.email)
                .build(),
        ).groups().map { it.groupName() }

        return AuthSessionResponse(
            signedIn = true,
            role = groups.firstOrNull(),
            email = attributes["email"] ?: input.email,
            name = attributes["name"],
        )
    }

    @PostMapping("/api/auth/refresh")
    fun refresh(request: HttpServletRequest, response: HttpServletResponse): AuthSessionResponse {
        val refreshToken = request.cookies?.firstOrNull { it.name == AuthCookies.REFRESH_TOKEN }?.value
            ?: throw UnauthenticatedException("No refresh token present")

        // Refresh tokens are bound to the app client that issued them.
        // Cookies from the email/password flow (clientId) and from Google
        // sign-in (oauthClientId, a separate confidential client — see
        // googleCallback) both land in the same cookie, so try the
        // email/password client first and fall back to the OAuth token
        // endpoint rather than asking the frontend to track which flow a
        // given session came from.
        val authResult = try {
            cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .clientId(cognitoProperties.clientId)
                    .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                    .authParameters(mapOf("REFRESH_TOKEN" to refreshToken))
                    .build(),
            ).authenticationResult()
        } catch (e: NotAuthorizedException) {
            if (cognitoProperties.oauthClientId.isBlank()) throw UnauthenticatedException("Refresh token is invalid or expired")
            val refreshed = try {
                exchangeOAuthToken(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken))
            } catch (oauthError: Exception) {
                throw UnauthenticatedException("Refresh token is invalid or expired")
            }
            setAccessTokenCookie(response, request.isSecure, refreshed["access_token"] as String, (refreshed["expires_in"] as Number).toLong())
            return AuthSessionResponse(signedIn = true)
        }

        // REFRESH_TOKEN_AUTH doesn't return a new refresh token — only the
        // access (+ID) token is refreshed; the original refresh token cookie
        // is left untouched until it naturally expires.
        setAccessTokenCookie(response, request.isSecure, authResult)
        return AuthSessionResponse(signedIn = true)
    }

    /** The frontend's "Continue with Google" link points straight here — no Cognito URL construction ever happens client-side. */
    @GetMapping("/api/auth/google/start")
    fun googleStart(response: HttpServletResponse) {
        val redirectUri = URLEncoder.encode(cognitoProperties.oauthRedirectUri, StandardCharsets.UTF_8)
        response.sendRedirect(
            "https://${cognitoProperties.oauthDomain}/oauth2/authorize" +
                "?client_id=${cognitoProperties.oauthClientId}" +
                "&response_type=code" +
                "&scope=openid+email+profile" +
                "&redirect_uri=$redirectUri" +
                "&identity_provider=Google",
        )
    }

    /**
     * Cognito Hosted UI redirects here after Google auth completes. First
     * sign-in for a given Google account has no Cognito group yet (unlike
     * register(), which assigns "buyer" inline) — JIT-assign it here, then
     * re-request the token so the access token cookie's first value already
     * carries cognito:groups, instead of leaving the buyer with a
     * groupless token until the next refresh.
     */
    @GetMapping("/api/auth/google/callback")
    fun googleCallback(
        @RequestParam(required = false) code: String?,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        if (code == null) {
            response.sendRedirect("${notificationProperties.frontendBaseUrl}/account/login?error=google_auth_failed")
            return
        }
        try {
            val tokenResponse = exchangeOAuthToken(mapOf("grant_type" to "authorization_code", "code" to code, "redirect_uri" to cognitoProperties.oauthRedirectUri))
            var accessToken = tokenResponse["access_token"] as String
            val refreshToken = tokenResponse["refresh_token"] as String
            var expiresIn = (tokenResponse["expires_in"] as Number).toLong()

            val username = jwtDecoder.decode(accessToken).getClaimAsString("username")
                ?: throw IllegalStateException("Google-issued access token has no username claim")
            val groups = cognitoClient.adminListGroupsForUser(
                AdminListGroupsForUserRequest.builder().userPoolId(cognitoProperties.userPoolId).username(username).build(),
            ).groups().map { it.groupName() }

            if (groups.isEmpty()) {
                addToGroupWithRetry(username, "buyer")
                val refreshed = exchangeOAuthToken(mapOf("grant_type" to "refresh_token", "refresh_token" to refreshToken))
                accessToken = refreshed["access_token"] as String
                expiresIn = (refreshed["expires_in"] as Number).toLong()
            }

            setAccessTokenCookie(response, request.isSecure, accessToken, expiresIn)
            val refreshCookie = ResponseCookie.from(AuthCookies.REFRESH_TOKEN, refreshToken)
                .httpOnly(true).secure(request.isSecure).sameSite("Lax").path("/").maxAge(REFRESH_TOKEN_MAX_AGE_SECONDS).build()
            response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())
            response.sendRedirect("${notificationProperties.frontendBaseUrl}/account")
        } catch (e: Exception) {
            log.warn("Google sign-in failed", e)
            response.sendRedirect("${notificationProperties.frontendBaseUrl}/account/login?error=google_auth_failed")
        }
    }

    /** POSTs to the Hosted UI's /oauth2/token endpoint — shared by the authorization_code exchange and both refresh_token paths above. */
    private fun exchangeOAuthToken(params: Map<String, String>): Map<String, Any> {
        val credentials = Base64.getEncoder().encodeToString(
            "${cognitoProperties.oauthClientId}:${cognitoProperties.oauthClientSecret}".toByteArray(StandardCharsets.UTF_8),
        )
        val body = LinkedMultiValueMap<String, String>()
        body.add("client_id", cognitoProperties.oauthClientId)
        params.forEach { (key, value) -> body.add(key, value) }
        return restClient.post()
            .uri("https://${cognitoProperties.oauthDomain}/oauth2/token")
            .header(HttpHeaders.AUTHORIZATION, "Basic $credentials")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .body(body)
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: throw IllegalStateException("Empty response from Cognito token endpoint")
    }

    @PostMapping("/api/auth/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): AuthSessionResponse {
        val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
        if (auth != null) {
            try {
                cognitoClient.adminUserGlobalSignOut(
                    AdminUserGlobalSignOutRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId)
                        .username(auth.token.subject)
                        .build(),
                )
            } catch (e: CognitoIdentityProviderException) {
                // Best-effort: still clear the local cookies below even if
                // the Cognito-side revocation call fails (e.g. transient
                // network error) — same "don't fail the primary action for
                // a secondary side effect" principle as OrderNotifier.
                log.warn("Failed to revoke Cognito session for {} during logout", auth.token.subject, e)
            }
        }
        clearAuthCookies(response, request.isSecure)
        return AuthSessionResponse(signedIn = false)
    }

    /**
     * Lets the frontend learn its own auth state — the tokens themselves
     * are httpOnly and unreadable by JS. Reads email/name from the DB-cached
     * CurrentActor row (JIT-provisioned already), not the access token —
     * the access token carries no profile claims at all (only ID tokens do,
     * and those never reach the backend), so this avoids an extra live
     * Cognito call on every session check.
     */
    @GetMapping("/api/auth/session")
    fun session(): AuthSessionResponse {
        val auth = SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken
            ?: return AuthSessionResponse(signedIn = false)
        val role = auth.authorities.firstOrNull()?.authority?.removePrefix("ROLE_")?.lowercase()
        val (email, name) = when (role) {
            "buyer" -> currentActor.buyerOrNull()?.let { it.email to it.name }
            "seller" -> currentActor.sellerOrNull()?.let { it.email to it.name }
            "admin" -> currentActor.adminOrNull()?.let { it.email to it.name }
            else -> null
        } ?: (null to null)
        return AuthSessionResponse(
            signedIn = true,
            role = role,
            email = email,
            name = name,
        )
    }

    private fun adminInitiateAuth(email: String, password: String): AuthenticationResultType {
        try {
            return cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .clientId(cognitoProperties.clientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(mapOf("USERNAME" to email, "PASSWORD" to password))
                    .build(),
            ).authenticationResult()
        } catch (e: NotAuthorizedException) {
            throw UnauthenticatedException("Invalid email or password")
        } catch (e: UserNotFoundException) {
            throw UnauthenticatedException("Invalid email or password")
        }
    }

    /** One inline retry for the Cognito-user-created-but-group-assignment-failed race (see plan notes) — no Lambda trigger needed for a gap this narrow. */
    private fun addToGroupWithRetry(username: String, groupName: String) {
        repeat(2) { attempt ->
            try {
                cognitoClient.adminAddUserToGroup(
                    AdminAddUserToGroupRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId)
                        .username(username)
                        .groupName(groupName)
                        .build(),
                )
                return
            } catch (e: CognitoIdentityProviderException) {
                if (attempt == 1) {
                    log.warn("Failed to add {} to Cognito group '{}' after retry — account exists but has no role until this is fixed manually", username, groupName, e)
                }
            }
        }
    }

    private fun setAuthCookies(response: HttpServletResponse, secure: Boolean, authResult: AuthenticationResultType) {
        setAccessTokenCookie(response, secure, authResult)
        val refreshCookie = ResponseCookie.from(AuthCookies.REFRESH_TOKEN, authResult.refreshToken())
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(REFRESH_TOKEN_MAX_AGE_SECONDS)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString())
    }

    private fun setAccessTokenCookie(response: HttpServletResponse, secure: Boolean, authResult: AuthenticationResultType) {
        setAccessTokenCookie(response, secure, authResult.accessToken(), authResult.expiresIn().toLong())
    }

    private fun setAccessTokenCookie(response: HttpServletResponse, secure: Boolean, accessToken: String, expiresInSeconds: Long) {
        val accessCookie = ResponseCookie.from(AuthCookies.ACCESS_TOKEN, accessToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(expiresInSeconds)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString())
    }

    private fun clearAuthCookies(response: HttpServletResponse, secure: Boolean) {
        listOf(AuthCookies.ACCESS_TOKEN, AuthCookies.REFRESH_TOKEN).forEach { name ->
            val expired = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build()
            response.addHeader(HttpHeaders.SET_COOKIE, expired.toString())
        }
    }
}
