package com.storepilot.backend.common.security

import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import java.time.Instant

class RateLimitFilterTest {
    private val filter = RateLimitFilter()

    private fun request(path: String, ip: String = "10.0.0.1") = MockHttpServletRequest("POST", path).apply { remoteAddr = ip }

    @Test
    fun `passes through untouched for a path with no rate-limit policy`() {
        val chain = mockk<FilterChain>(relaxed = true)
        val response = MockHttpServletResponse()

        repeat(50) { filter.doFilterInternal(request("/api/products"), response, chain) }

        verify(exactly = 50) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `allows requests up to the policy limit`() {
        val chain = mockk<FilterChain>(relaxed = true)

        repeat(10) {
            val response = MockHttpServletResponse()
            filter.doFilterInternal(request("/api/auth/login"), response, chain)
            assertEquals(200, response.status)
        }

        verify(exactly = 10) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `rejects the request once the policy limit is exceeded`() {
        val chain = mockk<FilterChain>(relaxed = true)
        repeat(10) { filter.doFilterInternal(request("/api/auth/login"), MockHttpServletResponse(), chain) }

        val response = MockHttpServletResponse()
        filter.doFilterInternal(request("/api/auth/login"), response, chain)

        assertEquals(429, response.status)
        assertTrue(response.contentAsString.contains("RATE_LIMITED"))
        verify(exactly = 10) { chain.doFilter(any(), any()) }
    }

    @Test
    fun `tracks limits independently per client IP`() {
        val chain = mockk<FilterChain>(relaxed = true)
        repeat(10) { filter.doFilterInternal(request("/api/auth/login", ip = "10.0.0.1"), MockHttpServletResponse(), chain) }

        val response = MockHttpServletResponse()
        filter.doFilterInternal(request("/api/auth/login", ip = "10.0.0.2"), response, chain)

        assertEquals(200, response.status)
    }

    @Test
    fun `tracks limits independently per rate-limited path`() {
        val chain = mockk<FilterChain>(relaxed = true)
        repeat(10) { filter.doFilterInternal(request("/api/auth/login"), MockHttpServletResponse(), chain) }

        val response = MockHttpServletResponse()
        filter.doFilterInternal(request("/api/auth/register"), response, chain)

        assertEquals(200, response.status)
    }

    @Test
    fun `window rolls over and resets the count once it has elapsed`() {
        val chain = mockk<FilterChain>(relaxed = true)
        val start = Instant.now()
        filter.clock = { start }
        repeat(10) { filter.doFilterInternal(request("/api/auth/login"), MockHttpServletResponse(), chain) }

        filter.clock = { start.plus(Duration.ofMinutes(2)) }
        val response = MockHttpServletResponse()
        filter.doFilterInternal(request("/api/auth/login"), response, chain)

        assertEquals(200, response.status)
    }

    @Test
    fun `evictStaleCounters removes entries older than the eviction cutoff`() {
        val chain = mockk<FilterChain>(relaxed = true)
        val start = Instant.now()
        filter.clock = { start }
        repeat(10) { filter.doFilterInternal(request("/api/auth/login"), MockHttpServletResponse(), chain) }

        filter.clock = { start.plus(Duration.ofMinutes(31)) }
        filter.evictStaleCounters()

        val response = MockHttpServletResponse()
        filter.doFilterInternal(request("/api/auth/login"), response, chain)

        assertEquals(200, response.status)
    }
}
