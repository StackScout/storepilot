package com.storepilot.backend.common.security

import com.storepilot.backend.admin.Admin
import com.storepilot.backend.admin.AdminRepository
import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.buyer.BuyerRepository
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import java.time.Instant

class CurrentActorTest {
    private val buyerRepository = mockk<BuyerRepository>()
    private val sellerRepository = mockk<SellerRepository>()
    private val adminRepository = mockk<AdminRepository>()
    private val cognitoClient = mockk<CognitoIdentityProviderClient>()
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-123")
    private val currentActor = CurrentActor(buyerRepository, sellerRepository, adminRepository, cognitoClient, cognitoProperties)

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

    @BeforeEach
    fun stubCognitoProfileLookup() {
        every {
            cognitoClient.adminGetUser(any<AdminGetUserRequest>())
        } returns AdminGetUserResponse.builder()
            .userAttributes(
                AttributeType.builder().name("email").value("actor@example.com").build(),
                AttributeType.builder().name("name").value("Actor Name").build(),
            )
            .build()
    }

    @Test
    fun `buyerOrNull returns null when no authentication is present`() {
        assertNull(currentActor.buyerOrNull())
    }

    @Test
    fun `buyerOrNull returns null when authenticated without the BUYER role`() {
        authenticateAs("sub-1", "SELLER")
        assertNull(currentActor.buyerOrNull())
    }

    @Test
    fun `buyerOrNull returns the existing row without JIT-provisioning when one already exists`() {
        authenticateAs("sub-1", "BUYER")
        val existing = Buyer(name = "Jane", email = "jane@example.com", cognitoSub = "sub-1")
        every { buyerRepository.findByCognitoSub("sub-1") } returns existing

        val result = currentActor.buyerOrNull()

        assertEquals(existing, result)
        verify(exactly = 0) { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) }
    }

    @Test
    fun `buyerOrNull JIT-provisions a new row linking an existing guest-checkout email match`() {
        authenticateAs("sub-1", "BUYER")
        every { buyerRepository.findByCognitoSub("sub-1") } returns null
        val guestRow = Buyer(name = "Guest", email = "actor@example.com", cognitoSub = "")
        every { buyerRepository.findByEmailIgnoreCase("actor@example.com") } returns guestRow
        every { buyerRepository.save(guestRow) } returns guestRow

        val result = currentActor.buyerOrNull()

        assertEquals("sub-1", guestRow.cognitoSub)
        assertEquals(guestRow, result)
    }

    @Test
    fun `sellerOrNull never JIT-provisions — null until onboarding creates a row`() {
        authenticateAs("sub-1", "SELLER")
        every { sellerRepository.findByCognitoSub("sub-1") } returns null

        assertNull(currentActor.sellerOrNull())
        verify(exactly = 0) { sellerRepository.save(any()) }
    }

    @Test
    fun `requireSeller throws ForbiddenException when no seller row exists`() {
        authenticateAs("sub-1", "SELLER")
        every { sellerRepository.findByCognitoSub("sub-1") } returns null

        assertThrows(ForbiddenException::class.java) { currentActor.requireSeller() }
    }

    @Test
    fun `requireAdmin JIT-provisions an admin row on first use`() {
        authenticateAs("sub-1", "ADMIN")
        every { adminRepository.findByCognitoSub("sub-1") } returns null
        val slot = io.mockk.slot<Admin>()
        every { adminRepository.save(capture(slot)) } answers { slot.captured }

        val result = currentActor.requireAdmin()

        assertEquals("sub-1", result.cognitoSub)
        assertEquals("actor@example.com", result.email)
    }

    @Test
    fun `isBuyer reflects the JWT's role claim, not row existence`() {
        authenticateAs("sub-1", "BUYER")
        assertEquals(true, currentActor.isBuyer())
    }

    @Test
    fun `a non-JWT authentication type such as TestingAuthenticationToken resolves to no role`() {
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken("sub-1", "n/a", "ROLE_ADMIN")
        assertNull(currentActor.adminOrNull())
    }
}
