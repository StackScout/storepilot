package com.storepilot.backend.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Authorization matrix, two layers deep:
 *  1. Coarse, filter-chain-level role gates from permissions.yml (hasRole
 *     checks) — "is this caller a seller/admin at all". See that file's
 *     header comment for its schema and the first-match-wins ordering
 *     [applyRule] relies on when it folds `rules` in list order.
 *  2. Fine, service-layer ownership checks via CurrentActor (e.g. "does
 *     this seller own THIS specific store") — Spring's URL matchers can't
 *     express per-resource ownership, only role.
 *
 * Guest-checkout endpoints stay permitAll but still get an authenticated
 * CurrentActor when a valid buyer token happens to be present (see
 * CookieBearerTokenResolver's doc comment for the one known edge case this
 * doesn't cover: an invalid/expired cookie on a permitAll route still 401s,
 * a Spring Security resource-server characteristic, not a bug here).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val endpointPermissionsProperties: EndpointPermissionsProperties,
) {
    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(CognitoGroupsAuthoritiesConverter())
        return converter
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtDecoder: JwtDecoder,
        jwtAuthenticationConverter: JwtAuthenticationConverter,
    ): SecurityFilterChain {
        http
            // JSON API, no browser form ever submits a state-changing
            // request cross-site; SameSite=Lax on the auth cookies is the
            // primary cross-site mitigation.
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(JsonAuthenticationEntryPoint())
                it.accessDeniedHandler(JsonAccessDeniedHandler())
            }
            .authorizeHttpRequests { auth ->
                endpointPermissionsProperties.rules
                    .fold(auth) { registry, rule -> applyRule(registry, rule) }
                    // Everything not matched by a permissions.yml rule above
                    // (e.g. /api/conversations/** — buyer or seller can both
                    // be a valid participant, so MessagingService.requireParticipant
                    // is the actual gate). Not expressible in permissions.yml
                    // since it's anyRequest(), not a path-pattern rule.
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .bearerTokenResolver(CookieBearerTokenResolver())
                    .jwt { jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }

    /**
     * Applies one permissions.yml rule and returns the registry so callers
     * can keep folding/chaining. Validates against APP_COGNITO_ROLES (the
     * same set CognitoGroupsAuthoritiesConverter grants authorities from)
     * rather than a separate hardcoded list here, and against the real
     * HttpMethod enum — an unrecognized value in either fails application
     * startup instead of silently misconfiguring auth.
     */
    private fun applyRule(
        registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
        rule: EndpointPermissionRule,
    ): AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry {
        val httpMethod =
            rule.method?.let {
                runCatching { HttpMethod.valueOf(it) }.getOrElse {
                    error("permissions.yml: unknown HTTP method '${rule.method}' for path '${rule.path}'")
                }
            }
        val matcher = if (httpMethod != null) registry.requestMatchers(httpMethod, rule.path) else registry.requestMatchers(rule.path)
        val roles = APP_COGNITO_ROLES.map { it.uppercase() }
        return when {
            rule.access == listOf("PUBLIC") -> matcher.permitAll()
            rule.access == listOf("AUTHENTICATED") -> matcher.authenticated()
            rule.access.isNotEmpty() && rule.access.all { it in roles } ->
                if (rule.access.size == 1) matcher.hasRole(rule.access[0]) else matcher.hasAnyRole(*rule.access.toTypedArray())
            else ->
                error(
                    "permissions.yml: invalid access ${rule.access} for ${rule.method ?: "ANY"} ${rule.path} — " +
                        "must be PUBLIC, AUTHENTICATED, or one or more of $roles (PUBLIC/AUTHENTICATED can't be combined with a role)",
                )
        }
    }
}
