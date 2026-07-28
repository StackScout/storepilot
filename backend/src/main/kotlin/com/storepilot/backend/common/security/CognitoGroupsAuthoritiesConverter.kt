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
 */
class CognitoGroupsAuthoritiesConverter : Converter<Jwt, Collection<GrantedAuthority>> {
    override fun convert(jwt: Jwt): Collection<GrantedAuthority> =
        jwt.getClaimAsStringList("cognito:groups")
            ?.map { SimpleGrantedAuthority("ROLE_${it.uppercase()}") }
            ?: emptyList()
}
