package com.storepilot.backend.common.security

import com.storepilot.backend.admin.Admin
import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.EmailNotVerifiedException
import com.storepilot.backend.common.UnauthenticatedException
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.StoreStaffService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminListGroupsForUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRespondToAuthChallengeRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AssociateSoftwareTokenResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType
import software.amazon.awssdk.services.cognitoidentityprovider.model.CodeMismatchException
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException
import software.amazon.awssdk.services.cognitoidentityprovider.model.EnableSoftwareTokenMfaException
import software.amazon.awssdk.services.cognitoidentityprovider.model.ExpiredCodeException
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException
import software.amazon.awssdk.services.cognitoidentityprovider.model.SetUserMfaPreferenceRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.VerifySoftwareTokenResponseType
import java.time.Instant
import java.util.UUID

class AuthControllerTest {
    private val cognitoClient = mockk<CognitoIdentityProviderClient>()
    private val cognitoProperties = CognitoProperties(
        userPoolId = "pool-123",
        oauthDomain = "storepilot-dev.auth.ap-southeast-2.amazoncognito.com",
        oauthClientId = "oauth-client-id",
        oauthRedirectUri = "https://example.test/api/auth/google/callback",
    )
    private val currentActor = mockk<CurrentActor>()
    private val notificationProperties = NotificationProperties()
    private val jwtDecoder = mockk<JwtDecoder>()
    private val emailVerificationService = mockk<EmailVerificationService>(relaxed = true)
    private val storeStaffService = mockk<StoreStaffService>(relaxed = true)
    private val controller = AuthController(
        cognitoClient, cognitoProperties, currentActor, notificationProperties, jwtDecoder, emailVerificationService, storeStaffService,
    )

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(sub: String, vararg roles: String) {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(sub)
            .claim("username", sub)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60))
            .build()
        val authorities: Collection<GrantedAuthority> = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt, authorities)
    }

    private fun request(secure: Boolean = false, vararg cookies: Cookie) = mockk<HttpServletRequest>(relaxed = true).also {
        every { it.isSecure } returns secure
        every { it.cookies } returns if (cookies.isEmpty()) null else cookies.toList().toTypedArray()
    }

    private fun response() = mockk<HttpServletResponse>(relaxed = true)

    // --- resolveGoogleSignInOutcome: the pure (existingRole x intent) decision table ---

    @Test
    fun `groupless plus buyer intent assigns buyer and lands on account`() {
        val outcome = resolveGoogleSignInOutcome(intent = "buyer", existingRole = null)
        assertEquals("buyer", outcome.groupToAssign)
        assertEquals("/account", outcome.redirectPath)
        assertFalse(outcome.rejected)
    }

    @Test
    fun `groupless plus seller intent assigns no group and lands on onboarding`() {
        val outcome = resolveGoogleSignInOutcome(intent = "seller", existingRole = null)
        assertNull(outcome.groupToAssign)
        assertEquals("/onboarding", outcome.redirectPath)
        assertFalse(outcome.rejected)
    }

    @Test
    fun `existing buyer plus buyer intent is a normal returning sign-in`() {
        val outcome = resolveGoogleSignInOutcome(intent = "buyer", existingRole = "buyer")
        assertNull(outcome.groupToAssign)
        assertEquals("/account", outcome.redirectPath)
        assertFalse(outcome.rejected)
    }

    @Test
    fun `existing seller plus seller intent is a normal returning sign-in`() {
        val outcome = resolveGoogleSignInOutcome(intent = "seller", existingRole = "seller")
        assertNull(outcome.groupToAssign)
        assertEquals("/dashboard", outcome.redirectPath)
        assertFalse(outcome.rejected)
    }

    @Test
    fun `existing buyer plus seller intent is rejected back to seller login`() {
        val outcome = resolveGoogleSignInOutcome(intent = "seller", existingRole = "buyer")
        assertTrue(outcome.rejected)
        assertEquals("/login?error=google_wrong_account_type&existingRole=buyer", outcome.redirectPath)
    }

    @Test
    fun `existing seller plus buyer intent is rejected back to buyer login`() {
        val outcome = resolveGoogleSignInOutcome(intent = "buyer", existingRole = "seller")
        assertTrue(outcome.rejected)
        assertEquals("/account/login?error=google_wrong_account_type&existingRole=seller", outcome.redirectPath)
    }

    @Test
    fun `an admin identity is rejected regardless of intent`() {
        assertTrue(resolveGoogleSignInOutcome(intent = "buyer", existingRole = "admin").rejected)
        assertTrue(resolveGoogleSignInOutcome(intent = "seller", existingRole = "admin").rejected)
    }

    @Test
    fun `loginPathFor picks the seller vs buyer login page`() {
        assertEquals("/login", loginPathFor("seller"))
        assertEquals("/account/login", loginPathFor("buyer"))
    }

    // --- resolveAppRole: filters out Cognito's auto-generated per-IdP group ---

    @Test
    fun `resolveAppRole ignores the autogenerated per-identity-provider group`() {
        // Reproduces the real bug: Cognito adds every Google-authenticated user to
        // "{userPoolId}_Google" alongside their real app role — a naive
        // groups.firstOrNull() can pick that instead of the real role depending on
        // list order.
        assertEquals("buyer", resolveAppRole(listOf("ap-southeast-2_NmGrljWsz_Google", "buyer")))
        assertEquals("buyer", resolveAppRole(listOf("buyer", "ap-southeast-2_NmGrljWsz_Google")))
        assertEquals("seller", resolveAppRole(listOf("ap-southeast-2_NmGrljWsz_Google", "seller")))
    }

    @Test
    fun `resolveAppRole returns null for a groupless identity even with the autogenerated group present`() {
        assertNull(resolveAppRole(listOf("ap-southeast-2_NmGrljWsz_Google")))
        assertNull(resolveAppRole(emptyList()))
    }

    @Test
    fun `resolveAppRole recognizes admin`() {
        assertEquals("admin", resolveAppRole(listOf("admin")))
    }

    // --- googleStart(): URL construction + validation ---

    @Test
    fun `googleStart defaults to buyer intent and carries it as state`() {
        val response = mockk<HttpServletResponse>(relaxed = true)
        val urlSlot = slot<String>()
        every { response.sendRedirect(capture(urlSlot)) } returns Unit

        controller.googleStart(intent = "buyer", response = response, platform = "web")

        assertTrue(urlSlot.captured.startsWith("https://storepilot-dev.auth.ap-southeast-2.amazoncognito.com/oauth2/authorize"))
        assertTrue(urlSlot.captured.contains("&identity_provider=Google"))
        assertTrue(urlSlot.captured.contains("&state=buyer"))
    }

    @Test
    fun `googleStart with seller intent carries seller as state`() {
        val response = mockk<HttpServletResponse>(relaxed = true)
        val urlSlot = slot<String>()
        every { response.sendRedirect(capture(urlSlot)) } returns Unit

        controller.googleStart(intent = "seller", response = response, platform = "web")

        assertTrue(urlSlot.captured.contains("&state=seller"))
    }

    @Test
    fun `googleStart rejects an invalid intent`() {
        val response = mockk<HttpServletResponse>(relaxed = true)
        assertThrows(IllegalArgumentException::class.java) {
            controller.googleStart(intent = "admin", response = response, platform = "web")
        }
    }

    // --- register() ---

    private fun registerInput(accountType: String = "buyer") = RegisterInput(name = "Jane Doe", email = "jane@example.com", password = "SuperSecret123!", accountType = accountType)

    @Test
    fun `register rejects an account type that isn't buyer or seller`() {
        assertThrows(IllegalArgumentException::class.java) { controller.register(registerInput(accountType = "admin")) }
    }

    @Test
    fun `register maps an existing-username error to a conflict`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } throws UsernameExistsException.builder().message("exists").build()

        assertThrows(ConflictException::class.java) { controller.register(registerInput()) }
    }

    @Test
    fun `register maps a weak-password error to a client error`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } throws InvalidPasswordException.builder().message("too weak").build()

        assertThrows(IllegalArgumentException::class.java) { controller.register(registerInput()) }
    }

    @Test
    fun `register grants the buyer group for a buyer registration and sends a code`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } returns AdminCreateUserResponse.builder().build()
        every { cognitoClient.adminSetUserPassword(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest>()) } returns mockk(relaxed = true)
        every { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) } returns mockk(relaxed = true)

        val result = controller.register(registerInput(accountType = "buyer"))

        assertEquals("jane@example.com", result.email)
        verify { cognitoClient.adminAddUserToGroup(match<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest> { it.groupName() == "buyer" }) }
        verify { emailVerificationService.sendCode("jane@example.com", "Jane Doe") }
    }

    @Test
    fun `register grants no group for a seller registration`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } returns AdminCreateUserResponse.builder().build()
        every { cognitoClient.adminSetUserPassword(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest>()) } returns mockk(relaxed = true)

        controller.register(registerInput(accountType = "seller"))

        verify(exactly = 0) { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) }
    }

    @Test
    fun `register tolerates a group-assignment failure that succeeds on retry`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } returns AdminCreateUserResponse.builder().build()
        every { cognitoClient.adminSetUserPassword(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest>()) } returns mockk(relaxed = true)
        every { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) } throws
            CognitoIdentityProviderException.builder().message("transient").build() andThen
            software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse.builder().build()

        controller.register(registerInput(accountType = "buyer"))

        verify(exactly = 2) { cognitoClient.adminAddUserToGroup(any<software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest>()) }
    }

    // --- verifyEmail() / resendVerificationCode() ---

    @Test
    fun `verifyEmail confirms the code and flips email_verified to true`() {
        val input = VerifyEmailInput(email = "jane@example.com", code = "123456")
        val attrSlot = slot<AdminUpdateUserAttributesRequest>()
        every { cognitoClient.adminUpdateUserAttributes(capture(attrSlot)) } returns mockk(relaxed = true)

        controller.verifyEmail(input)

        verify { emailVerificationService.verifyCode("jane@example.com", "123456") }
        assertEquals("true", attrSlot.captured.userAttributes().first { it.name() == "email_verified" }.value())
    }

    @Test
    fun `resendVerificationCode silently no-ops for an unknown email`() {
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } throws UserNotFoundException.builder().message("not found").build()

        controller.resendVerificationCode(ResendVerificationInput(email = "ghost@example.com"))

        verify(exactly = 0) { emailVerificationService.sendCode(any(), any()) }
    }

    @Test
    fun `resendVerificationCode sends using the account's name`() {
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns AdminGetUserResponse.builder()
            .userAttributes(AttributeType.builder().name("name").value("Jane Doe").build())
            .build()

        controller.resendVerificationCode(ResendVerificationInput(email = "jane@example.com"))

        verify { emailVerificationService.sendCode("jane@example.com", "Jane Doe") }
    }

    @Test
    fun `resendVerificationCode falls back to the email when no name attribute exists`() {
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns AdminGetUserResponse.builder().build()

        controller.resendVerificationCode(ResendVerificationInput(email = "jane@example.com"))

        verify { emailVerificationService.sendCode("jane@example.com", "jane@example.com") }
    }

    // --- login() / mfaChallenge() / completeLogin() ---

    private fun verifiedUserAttributes(email: String = "jane@example.com", name: String? = "Jane Doe") = AdminGetUserResponse.builder()
        .userAttributes(
            listOfNotNull(
                AttributeType.builder().name("email").value(email).build(),
                AttributeType.builder().name("email_verified").value("true").build(),
                name?.let { AttributeType.builder().name("name").value(it).build() },
            ),
        ).build()

    private fun groupsResponse(vararg groups: String) = AdminListGroupsForUserResponse.builder()
        .groups(groups.map { GroupType.builder().groupName(it).build() })
        .build()

    private fun authResult(accessToken: String = "access-1", refreshToken: String = "refresh-1", expiresIn: Int = 3600) = AuthenticationResultType.builder()
        .accessToken(accessToken).refreshToken(refreshToken).expiresIn(expiresIn).build()

    @Test
    fun `login rejects invalid credentials`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } throws NotAuthorizedException.builder().message("bad creds").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.login(LoginInput(email = "jane@example.com", password = "wrong"), request(), response())
        }
    }

    @Test
    fun `login rejects an unknown user the same way as a wrong password`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } throws UserNotFoundException.builder().message("no user").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.login(LoginInput(email = "ghost@example.com", password = "whatever"), request(), response())
        }
    }

    @Test
    fun `login returns an MFA challenge without setting any cookies`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } returns AdminInitiateAuthResponse.builder()
            .challengeName(ChallengeNameType.SOFTWARE_TOKEN_MFA).session("mfa-session-1").build()
        val res = response()

        val result = controller.login(LoginInput(email = "jane@example.com", password = "pw"), request(), res)

        assertFalse(result.signedIn)
        assertTrue(result.mfaRequired)
        assertEquals("mfa-session-1", result.mfaSession)
        verify(exactly = 0) { res.addHeader(any(), any()) }
    }

    @Test
    fun `login rejects an unverified account`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } returns AdminInitiateAuthResponse.builder()
            .authenticationResult(authResult()).build()
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns AdminGetUserResponse.builder()
            .userAttributes(AttributeType.builder().name("email_verified").value("false").build())
            .build()

        assertThrows(EmailNotVerifiedException::class.java) {
            controller.login(LoginInput(email = "jane@example.com", password = "pw"), request(), response())
        }
    }

    @Test
    fun `login succeeds, sets cookies, and reports the resolved role`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } returns AdminInitiateAuthResponse.builder()
            .authenticationResult(authResult()).build()
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns verifiedUserAttributes()
        every { cognitoClient.adminListGroupsForUser(any<AdminListGroupsForUserRequest>()) } returns groupsResponse("buyer")
        val res = response()

        val result = controller.login(LoginInput(email = "jane@example.com", password = "pw"), request(), res)

        assertTrue(result.signedIn)
        assertEquals("buyer", result.role)
        assertEquals("jane@example.com", result.email)
        assertEquals("Jane Doe", result.name)
        assertEquals("access-1", result.accessToken)
        verify(atLeast = 2) { res.addHeader("Set-Cookie", any()) }
    }

    @Test
    fun `mfaChallenge maps a wrong code to unauthenticated`() {
        every { cognitoClient.adminRespondToAuthChallenge(any<AdminRespondToAuthChallengeRequest>()) } throws CodeMismatchException.builder().message("bad code").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.mfaChallenge(MfaChallengeInput(email = "jane@example.com", session = "s", code = "000000"), request(), response())
        }
    }

    @Test
    fun `mfaChallenge maps an expired code to unauthenticated`() {
        every { cognitoClient.adminRespondToAuthChallenge(any<AdminRespondToAuthChallengeRequest>()) } throws ExpiredCodeException.builder().message("expired").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.mfaChallenge(MfaChallengeInput(email = "jane@example.com", session = "s", code = "000000"), request(), response())
        }
    }

    @Test
    fun `mfaChallenge maps an expired session to unauthenticated`() {
        every { cognitoClient.adminRespondToAuthChallenge(any<AdminRespondToAuthChallengeRequest>()) } throws NotAuthorizedException.builder().message("expired session").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.mfaChallenge(MfaChallengeInput(email = "jane@example.com", session = "s", code = "000000"), request(), response())
        }
    }

    @Test
    fun `mfaChallenge completes login on a correct code`() {
        every { cognitoClient.adminRespondToAuthChallenge(any<AdminRespondToAuthChallengeRequest>()) } returns mockk { every { authenticationResult() } returns authResult() }
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns verifiedUserAttributes()
        every { cognitoClient.adminListGroupsForUser(any<AdminListGroupsForUserRequest>()) } returns groupsResponse("seller")

        val result = controller.mfaChallenge(MfaChallengeInput(email = "jane@example.com", session = "s", code = "123456"), request(), response())

        assertTrue(result.signedIn)
        assertEquals("seller", result.role)
    }

    // --- MFA setup/verify/disable/status ---

    @Test
    fun `mfaSetup requires an authenticated caller`() {
        assertThrows(UnauthenticatedException::class.java) { controller.mfaSetup() }
    }

    @Test
    fun `mfaSetup returns a fresh secret and otpauth URI`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.associateSoftwareToken(any<AssociateSoftwareTokenRequest>()) } returns AssociateSoftwareTokenResponse.builder().secretCode("SECRET123").build()
        every { cognitoClient.getUser(any<GetUserRequest>()) } returns GetUserResponse.builder()
            .userAttributes(AttributeType.builder().name("email").value("jane@example.com").build())
            .build()

        val result = controller.mfaSetup()

        assertEquals("SECRET123", result.secret)
        assertTrue(result.otpauthUri.contains("SECRET123"))
        assertTrue(result.otpauthUri.startsWith("otpauth://totp/"))
    }

    @Test
    fun `mfaSetup falls back to a generic label when the account has no email attribute`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.associateSoftwareToken(any<AssociateSoftwareTokenRequest>()) } returns AssociateSoftwareTokenResponse.builder().secretCode("SECRET123").build()
        every { cognitoClient.getUser(any<GetUserRequest>()) } returns GetUserResponse.builder().build()

        val result = controller.mfaSetup()

        assertTrue(result.otpauthUri.contains("StorePilot%3Aaccount"))
    }

    @Test
    fun `mfaVerify rejects a mismatched code`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.verifySoftwareToken(any<VerifySoftwareTokenRequest>()) } throws CodeMismatchException.builder().message("bad").build()

        assertThrows(IllegalArgumentException::class.java) { controller.mfaVerify(MfaVerifyInput(code = "000000")) }
    }

    @Test
    fun `mfaVerify rejects when Cognito refuses to enable MFA`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.verifySoftwareToken(any<VerifySoftwareTokenRequest>()) } throws EnableSoftwareTokenMfaException.builder().message("bad").build()

        assertThrows(IllegalArgumentException::class.java) { controller.mfaVerify(MfaVerifyInput(code = "000000")) }
    }

    @Test
    fun `mfaVerify rejects a non-success verification status`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.verifySoftwareToken(any<VerifySoftwareTokenRequest>()) } returns VerifySoftwareTokenResponse.builder().status(VerifySoftwareTokenResponseType.ERROR).build()

        assertThrows(IllegalArgumentException::class.java) { controller.mfaVerify(MfaVerifyInput(code = "000000")) }
    }

    @Test
    fun `mfaVerify enables MFA on success`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.verifySoftwareToken(any<VerifySoftwareTokenRequest>()) } returns VerifySoftwareTokenResponse.builder().status(VerifySoftwareTokenResponseType.SUCCESS).build()
        val slot = slot<SetUserMfaPreferenceRequest>()
        every { cognitoClient.setUserMFAPreference(capture(slot)) } returns mockk(relaxed = true)

        controller.mfaVerify(MfaVerifyInput(code = "123456"))

        assertTrue(slot.captured.softwareTokenMfaSettings().enabled())
    }

    @Test
    fun `mfaDisable turns MFA off`() {
        authenticateAs("sub-1", "BUYER")
        val slot = slot<SetUserMfaPreferenceRequest>()
        every { cognitoClient.setUserMFAPreference(capture(slot)) } returns mockk(relaxed = true)

        controller.mfaDisable()

        assertFalse(slot.captured.softwareTokenMfaSettings().enabled())
    }

    @Test
    fun `mfaStatus reports enabled when TOTP is in the user's MFA setting list`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.getUser(any<GetUserRequest>()) } returns GetUserResponse.builder().userMFASettingList("SOFTWARE_TOKEN_MFA").build()

        assertTrue(controller.mfaStatus().enabled)
    }

    @Test
    fun `mfaStatus reports disabled when TOTP isn't enrolled`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.getUser(any<GetUserRequest>()) } returns GetUserResponse.builder().build()

        assertFalse(controller.mfaStatus().enabled)
    }

    // --- refresh() ---

    @Test
    fun `refresh throws when there's no refresh token anywhere`() {
        assertThrows(UnauthenticatedException::class.java) { controller.refresh(null, request(), response()) }
    }

    @Test
    fun `refresh uses the request body token when no cookie is present`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } returns AdminInitiateAuthResponse.builder().authenticationResult(authResult()).build()

        val result = controller.refresh(RefreshInput(refreshToken = "refresh-from-body"), request(), response())

        assertTrue(result.signedIn)
        assertEquals("access-1", result.accessToken)
    }

    @Test
    fun `refresh prefers the cookie token over the request body`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } returns AdminInitiateAuthResponse.builder().authenticationResult(authResult()).build()
        val slot = slot<AdminInitiateAuthRequest>()
        every { cognitoClient.adminInitiateAuth(capture(slot)) } returns AdminInitiateAuthResponse.builder().authenticationResult(authResult()).build()

        controller.refresh(RefreshInput(refreshToken = "refresh-from-body"), request(cookies = arrayOf(Cookie(AuthCookies.REFRESH_TOKEN, "refresh-from-cookie"))), response())

        assertEquals("refresh-from-cookie", slot.captured.authParameters()["REFRESH_TOKEN"])
    }

    @Test
    fun `refresh rejects an invalid token when no OAuth client is configured to fall back to`() {
        every { cognitoClient.adminInitiateAuth(any<AdminInitiateAuthRequest>()) } throws NotAuthorizedException.builder().message("invalid").build()

        assertThrows(UnauthenticatedException::class.java) {
            controller.refresh(RefreshInput(refreshToken = "expired"), request(), response())
        }
    }

    // --- logout() ---

    @Test
    fun `logout clears cookies without calling Cognito when there's no session`() {
        val res = response()

        val result = controller.logout(request(), res)

        assertFalse(result.signedIn)
        verify(exactly = 0) { cognitoClient.adminUserGlobalSignOut(any<AdminUserGlobalSignOutRequest>()) }
        verify(atLeast = 2) { res.addHeader("Set-Cookie", any()) }
    }

    @Test
    fun `logout revokes the Cognito session for a signed-in caller`() {
        authenticateAs("sub-1", "BUYER")
        val slot = slot<AdminUserGlobalSignOutRequest>()
        every { cognitoClient.adminUserGlobalSignOut(capture(slot)) } returns mockk(relaxed = true)

        controller.logout(request(), response())

        assertEquals("sub-1", slot.captured.username())
    }

    @Test
    fun `logout still clears cookies when Cognito revocation fails`() {
        authenticateAs("sub-1", "BUYER")
        every { cognitoClient.adminUserGlobalSignOut(any<AdminUserGlobalSignOutRequest>()) } throws CognitoIdentityProviderException.builder().message("down").build()
        val res = response()

        val result = controller.logout(request(), res)

        assertFalse(result.signedIn)
        verify(atLeast = 2) { res.addHeader("Set-Cookie", any()) }
    }

    // --- session() ---

    @Test
    fun `session reports signed out when there's no authentication`() {
        assertFalse(controller.session().signedIn)
    }

    @Test
    fun `session resolves a buyer's email and name via currentActor`() {
        authenticateAs("sub-1", "BUYER")
        every { currentActor.buyerOrNull() } returns Buyer(name = "Jane", email = "jane@example.com").apply { id = UUID.randomUUID() }

        val result = controller.session()

        assertTrue(result.signedIn)
        assertEquals("buyer", result.role)
        assertEquals("jane@example.com", result.email)
    }

    @Test
    fun `session resolves a seller via currentActor`() {
        authenticateAs("sub-1", "SELLER")
        every { currentActor.sellerOrNull() } returns Seller(cognitoSub = "sub-1", email = "seller@example.com", name = "Sam Seller").apply { id = UUID.randomUUID() }

        val result = controller.session()

        assertEquals("seller", result.role)
        assertEquals("seller@example.com", result.email)
    }

    @Test
    fun `session resolves an admin via currentActor`() {
        authenticateAs("sub-1", "ADMIN")
        every { currentActor.adminOrNull() } returns Admin(cognitoSub = "sub-1", email = "admin@example.com", name = "Ada Admin").apply { id = UUID.randomUUID() }

        val result = controller.session()

        assertEquals("admin", result.role)
        assertEquals("admin@example.com", result.email)
    }

    @Test
    fun `session reports a null role for a groupless identity`() {
        authenticateAs("sub-1")

        val result = controller.session()

        assertTrue(result.signedIn)
        assertNull(result.role)
        assertNull(result.email)
    }

    // --- googleCallback(): early-exit branch only (no live network call) ---

    @Test
    fun `googleCallback redirects with an error when Cognito returns no code`() {
        val res = response()
        val urlSlot = slot<String>()
        every { res.sendRedirect(capture(urlSlot)) } returns Unit

        controller.googleCallback(code = null, state = "buyer:web", request = request(), response = res)

        assertTrue(urlSlot.captured.contains("error=google_auth_failed"))
    }
}
