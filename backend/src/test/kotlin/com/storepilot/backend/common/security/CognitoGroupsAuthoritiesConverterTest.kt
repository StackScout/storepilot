package com.storepilot.backend.common.security

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

class CognitoGroupsAuthoritiesConverterTest {
    private val converter = CognitoGroupsAuthoritiesConverter()

    private fun jwtWithGroups(groups: List<String>?): Jwt =
        mockk<Jwt> { every { getClaimAsStringList("cognito:groups") } returns groups }

    @Test
    fun `maps a single app-recognized group to its ROLE authority`() {
        val authorities = converter.convert(jwtWithGroups(listOf("seller")))
        assertEquals(setOf(SimpleGrantedAuthority("ROLE_SELLER")), authorities.toSet())
    }

    @Test
    fun `filters out Cognito's auto-generated identity-provider group`() {
        val authorities = converter.convert(jwtWithGroups(listOf("buyer", "ap-southeast-2_abc123_Google")))
        assertEquals(setOf(SimpleGrantedAuthority("ROLE_BUYER")), authorities.toSet())
    }

    @Test
    fun `maps every recognized role at once when somehow present together`() {
        val authorities = converter.convert(jwtWithGroups(listOf("admin", "seller")))
        assertEquals(setOf(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_SELLER")), authorities.toSet())
    }

    @Test
    fun `returns no authorities when the claim is absent`() {
        val authorities = converter.convert(jwtWithGroups(null))
        assertTrue(authorities.isEmpty())
    }

    @Test
    fun `returns no authorities when every group is unrecognized`() {
        val authorities = converter.convert(jwtWithGroups(listOf("some-other-group")))
        assertTrue(authorities.isEmpty())
    }
}
