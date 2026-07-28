package com.storepilot.backend.common.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver

/** Cookie names shared between this resolver and AuthController (which sets/clears them). */
object AuthCookies {
    const val ACCESS_TOKEN = "storepilot_access_token"
    const val REFRESH_TOKEN = "storepilot_refresh_token"
}

/**
 * Reads the JWT from an httpOnly cookie instead of the `Authorization`
 * header — keeps the token out of reach of JS (meaningfully reduces XSS
 * blast radius for an app holding bank details and shipping PII) while
 * still letting the browser call the Spring backend directly: production is
 * same-origin via the Caddy reverse proxy, so the cookie is sent
 * automatically; local dev needs `credentials: "include"` on fetches (see
 * api-client.ts) plus `allowCredentials(true)` in WebConfig's CORS setup.
 *
 * Returns null (never throws) when the cookie is absent, so `permitAll()`
 * routes proceed with an empty SecurityContext rather than needing a
 * second parallel filter chain. Known trade-off: if the cookie IS present
 * but the token inside it is invalid/expired, Spring Security's resource
 * server support still fails the whole request with 401 even on a
 * permitAll route (this happens in ExceptionTranslationFilter, before
 * authorization rules are evaluated) — so a guest with a stale cookie can
 * see a 401 on a nominally-public endpoint instead of falling through
 * anonymously. In practice this self-resolves for logged-in users via the
 * frontend's 401-refresh-retry interceptor; a genuine guest with a
 * corrupted cookie value is the only case left affected — not fixed here
 * since a full fix needs a custom AuthenticationEntryPoint that inspects
 * the request path, which is real complexity for a narrow edge case.
 */
class CookieBearerTokenResolver : BearerTokenResolver {
    override fun resolve(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == AuthCookies.ACCESS_TOKEN }?.value?.takeIf { it.isNotBlank() }
}
