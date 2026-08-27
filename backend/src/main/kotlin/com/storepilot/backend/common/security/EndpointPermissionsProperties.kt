package com.storepilot.backend.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Binds permissions.yml (imported via application.yml's spring.config.import)
 * — see that file's header comment for the schema and the first-match-wins
 * ordering SecurityConfig.securityFilterChain relies on when it applies
 * `rules` in list order. A plain data class, not validated here — invalid
 * `access`/`method` values are rejected in SecurityConfig at startup, where
 * the set of valid roles actually lives (APP_COGNITO_ROLES).
 */
@ConfigurationProperties(prefix = "endpoint-permissions")
data class EndpointPermissionsProperties(
    val rules: List<EndpointPermissionRule> = emptyList(),
)

data class EndpointPermissionRule(
    val path: String,
    /**
     * A single value ("PUBLIC", "AUTHENTICATED", or a role name) or a YAML
     * list of role names for hasAnyRole (e.g. `access: [SELLER, ADMIN]`) —
     * Spring's config binder converts a lone scalar into a one-element list
     * automatically, so every existing single-role rule keeps working
     * unchanged. PUBLIC/AUTHENTICATED are only valid alone, never combined
     * with a role — enforced in SecurityConfig.applyRule.
     */
    val access: List<String>,
    /** Null matches every HTTP method — mirrors requestMatchers(path) with no HttpMethod overload. */
    val method: String? = null,
)
