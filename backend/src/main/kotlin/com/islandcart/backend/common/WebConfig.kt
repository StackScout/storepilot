package com.islandcart.backend.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Lets the Next.js frontend (a different origin, at least in local dev —
 * production is same-origin via the Caddy reverse proxy) call this API
 * directly from the browser. `allowCredentials(true)` is required for the
 * httpOnly auth cookies (see common/security/CookieBearerTokenResolver) to
 * be sent cross-origin at all — the frontend's fetches need
 * `credentials: "include"` to match. SecurityConfig's `.cors(withDefaults())`
 * picks this WebMvcConfigurer config up automatically; no separate
 * CorsConfigurationSource bean is needed.
 *
 * allowed-origins is a comma-separated list (app.cors-allowed-origins) so
 * the deployed frontend's origin can be added without a code change — see
 * application.yml. Spring rejects allowCredentials(true) combined with a
 * wildcard origin, so this must always be an explicit list, never "*".
 */
@Configuration
class WebConfig(
    @Value("\${app.cors-allowed-origins}") private val allowedOrigins: String,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins(*allowedOrigins.split(",").map { it.trim() }.toTypedArray())
            .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }
}
