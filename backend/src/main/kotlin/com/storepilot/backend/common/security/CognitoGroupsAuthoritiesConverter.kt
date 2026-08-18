package com.storepilot.backend.common.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Maps the `cognito:groups` claim (buyer/seller/admin) to Spring Security
 * authorities (ROLE_BUYER/ROLE_SELLER/ROLE_ADMIN). This is the only source
 * of role/authority in the app — never a database column — so removing a
 * user from a Cognito group revokes their role the moment their current
 * token expires, with no separate revocation step needed anywhere else.
 *
 * Filtered through APP_COGNITO_ROLES (AuthController.kt, same package) —
 * Cognito auto-creates one extra group per configured identity provider
 * (e.g. "{userPoolId}_Google") and silently adds every federated user to
 * it alongside their real buyer/seller/admin group. Without this filter a
 * Google-authenticated user would pick up a bogus
 * ROLE_{USERPOOLID}_GOOGLE authority too.
 */
class CognitoGroupsAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> =
        jwt.getClaimAsStringList("cognito:groups")
            ?.filter { it in APP_COGNITO_ROLES }
            ?.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") }
            ?: emptyList()
}
