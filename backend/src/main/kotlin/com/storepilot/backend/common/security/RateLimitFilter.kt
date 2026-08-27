package com.storepilot.backend.common.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** [maxRequests] allowed per [window], per client IP, for one exact request path. */
private data class RateLimitPolicy(val maxRequests: Int, val window: Duration)

/**
 * Every endpoint reachable without authentication (see permissions.yml's
 * PUBLIC rules) that either costs real money to abuse (an email/SMS send)
 * or is a brute-force target (a password/code check) — hand-picked rather
 * than a single global default, since a legitimate refresh-token call is
 * naturally much more frequent than a legitimate registration.
 */
private val RATE_LIMIT_POLICIES: Map<String, RateLimitPolicy> = mapOf(
    "/api/auth/login" to RateLimitPolicy(10, Duration.ofMinutes(1)),
    "/api/auth/mfa/challenge" to RateLimitPolicy(10, Duration.ofMinutes(1)),
    "/api/auth/register" to RateLimitPolicy(5, Duration.ofMinutes(10)),
    "/api/auth/resend-verification-code" to RateLimitPolicy(5, Duration.ofMinutes(10)),
    "/api/auth/refresh" to RateLimitPolicy(30, Duration.ofMinutes(1)),
    "/api/auth/google/start" to RateLimitPolicy(20, Duration.ofMinutes(1)),
    "/api/auth/google/callback" to RateLimitPolicy(20, Duration.ofMinutes(1)),
    "/api/orders/lookup/request-code" to RateLimitPolicy(5, Duration.ofMinutes(10)),
    "/api/bookings/lookup/request-code" to RateLimitPolicy(5, Duration.ofMinutes(10)),
    "/api/orders/lookup/verify" to RateLimitPolicy(10, Duration.ofMinutes(10)),
    "/api/bookings/lookup/verify" to RateLimitPolicy(10, Duration.ofMinutes(10)),
)

/** Mutable per-(ip, path) fixed-window counter. */
private class RequestCounter(@Volatile var windowStart: Instant, @Volatile var count: Int)

/**
 * Hand-rolled in-memory fixed-window limiter — no external dependency, same
 * "single-JVM only" deployment assumption SseHub.kt already documents for
 * this app (a multi-instance deployment would need a shared store, e.g.
 * Redis, instead). Registered ahead of the OAuth2 resource server's bearer-
 * token filter (see SecurityConfig) so it also protects PUBLIC/permitAll
 * endpoints, not just authenticated ones.
 *
 * A fixed (not sliding) window is a deliberate simplicity tradeoff: it lets
 * a caller burst up to 2x [RateLimitPolicy.maxRequests] right at a window
 * boundary, which is fine for the abuse this guards against (sustained
 * brute-force/spam), not full precision throttling.
 */
@Component
class RateLimitFilter : OncePerRequestFilter() {
    private val counters = ConcurrentHashMap<String, RequestCounter>()

    /** Overridable in tests (RateLimitFilterTest) so window-rollover/eviction can be exercised without sleeping real time. */
    internal var clock: () -> Instant = { Instant.now() }

    // Public (widened from OncePerRequestFilter's protected) so RateLimitFilterTest
    // can call it directly without standing up a real Spring filter chain.
    public override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val policy = RATE_LIMIT_POLICIES[request.requestURI]
        if (policy == null) {
            filterChain.doFilter(request, response)
            return
        }

        val now = clock()
        var exceeded = false
        counters.compute("${clientIp(request)}:${request.requestURI}") { _, existing ->
            val counter = existing ?: RequestCounter(now, 0)
            if (Duration.between(counter.windowStart, now) > policy.window) {
                counter.windowStart = now
                counter.count = 0
            }
            counter.count++
            exceeded = counter.count > policy.maxRequests
            counter
        }

        if (exceeded) {
            response.status = 429
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":{"code":"RATE_LIMITED","message":"Too many requests — please try again shortly"}}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    /** Sweeps counters whose window closed a while ago, so a long-running instance's map doesn't grow unbounded with stale (ip, path) entries. */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    fun evictStaleCounters() {
        val cutoff = clock().minus(Duration.ofMinutes(30))
        counters.entries.removeIf { it.value.windowStart.isBefore(cutoff) }
    }

    private fun clientIp(request: HttpServletRequest): String = request.remoteAddr
}
