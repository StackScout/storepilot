package com.islandcart.backend.common

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Lets the Next.js frontend (a different origin) call this API directly
 * from the browser. No credentials/cookies are shared across origins — see
 * docs/gaps-and-assumptions.md and CLAUDE.md's auth notes: the frontend's
 * session stays entirely client-side (its own cookie), and every endpoint
 * here trusts explicit storeId/buyerId parameters rather than verifying a
 * credential, matching the mock's existing (documented, unfixed) security
 * posture — this is not new scope, just not re-adding something that was
 * never there.
 *
 * allowed-origins is a comma-separated list (app.cors-allowed-origins) so
 * the deployed frontend's origin can be added without a code change — see
 * application.yml.
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
    }
}
