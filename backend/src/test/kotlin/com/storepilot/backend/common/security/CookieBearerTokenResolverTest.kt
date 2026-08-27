package com.storepilot.backend.common.security

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest

class CookieBearerTokenResolverTest {
    private val resolver = CookieBearerTokenResolver()

    @Test
    fun `resolves the token from the access token cookie`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(AuthCookies.ACCESS_TOKEN, "cookie-jwt"))

        assertEquals("cookie-jwt", resolver.resolve(request))
    }

    @Test
    fun `ignores unrelated cookies`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie("some_other_cookie", "irrelevant"))

        assertNull(resolver.resolve(request))
    }

    @Test
    fun `treats a blank cookie value as absent`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(AuthCookies.ACCESS_TOKEN, ""))

        assertNull(resolver.resolve(request))
    }

    @Test
    fun `falls back to the Authorization header when no cookie is present`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer header-jwt")

        assertEquals("header-jwt", resolver.resolve(request))
    }

    @Test
    fun `prefers the cookie over the Authorization header when both are present`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(AuthCookies.ACCESS_TOKEN, "cookie-jwt"))
        request.addHeader("Authorization", "Bearer header-jwt")

        assertEquals("cookie-jwt", resolver.resolve(request))
    }

    @Test
    fun `returns null when neither a cookie nor a header is present`() {
        val request = MockHttpServletRequest()
        assertNull(resolver.resolve(request))
    }
}
