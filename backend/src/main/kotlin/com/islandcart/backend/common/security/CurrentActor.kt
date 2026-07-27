package com.islandcart.backend.common.security

import com.islandcart.backend.admin.Admin
import com.islandcart.backend.admin.AdminRepository
import com.islandcart.backend.buyer.Buyer
import com.islandcart.backend.buyer.BuyerRepository
import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.seller.Seller
import com.islandcart.backend.seller.SellerRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest

/**
 * Resolves the current request's authenticated identity to the matching
 * Buyer/Seller/Admin domain row. Buyer/Admin rows are JIT-provisioned here
 * on first use (profile-data cache only); Seller rows are never
 * JIT-provisioned (see Seller.kt) — sellerOrNull returns null until
 * onboarding creates one.
 *
 * IMPORTANT: role/authorization NEVER comes from whether one of these rows
 * exists — it always comes from the JWT's `cognito:groups` claim (mapped to
 * ROLE_* authorities by CognitoGroupsAuthoritiesConverter and enforced by
 * SecurityConfig's hasRole(...) matchers). These methods resolve WHICH row,
 * after the role is already established — never grant a role themselves.
 *
 * The access token (what the API actually validates — see
 * CookieBearerTokenResolver) carries only `sub`/`cognito:groups`/etc, not
 * profile attributes like email/name (those live on the ID token, which
 * never reaches the backend). So JIT provisioning makes one AdminGetUser
 * call on first login per identity to fetch email/name — cheap, since every
 * later request hits the cognitoSub-keyed DB row directly with no extra
 * Cognito API call.
 */
@Component
class CurrentActor(
    private val buyerRepository: BuyerRepository,
    private val sellerRepository: SellerRepository,
    private val adminRepository: AdminRepository,
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
) {
    private fun jwtOrNull(): Jwt? =
        (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)?.token

    private fun hasRole(role: String): Boolean =
        (SecurityContextHolder.getContext().authentication as? JwtAuthenticationToken)
            ?.authorities?.any { it.authority == "ROLE_$role" } ?: false

    /**
     * This pool's UsernameAttributes is `email`, which makes Cognito
     * auto-generate an opaque username for the account — that generated
     * value equals the `sub` claim (verified against this project's dev
     * pool), so `sub` is a valid AdminGetUser lookup key here.
     */
    private fun fetchProfileFromCognito(sub: String): Pair<String, String> {
        val request = AdminGetUserRequest.builder()
            .userPoolId(cognitoProperties.userPoolId)
            .username(sub)
            .build()
        val attributes = cognitoClient.adminGetUser(request).userAttributes()
            .associate { it.name() to it.value() }
        val email = requireNotNull(attributes["email"]) { "Cognito user $sub has no email attribute" }
        val name = attributes["name"] ?: email
        return email to name
    }

    /**
     * Null for a guest (no token, or a token with no buyer role) — never
     * throws. This may JIT-provision (write) a new row on a caller's first
     * request, so — unintuitively for a "just resolve who's calling"
     * method — every caller must run in a genuinely writable transaction,
     * never one marked readOnly = true (a common @Service class-level
     * default for query-heavy services). Nesting this inside a read-only
     * transaction doesn't just skip the write silently — Postgres rejects
     * it outright ("cannot execute INSERT in a read-only transaction"),
     * and empirically this still happens even with a REQUIRES_NEW
     * propagation on this method, so that's not a safe workaround either;
     * the caller's own transaction must not be read-only. See
     * BuyerService.getCurrent() and OrderService.listByCurrentBuyer() for
     * the pattern: override the class default with a plain @Transactional
     * on any method that resolves the current buyer/admin.
     */
    @Transactional
    fun buyerOrNull(): Buyer? {
        val jwt = jwtOrNull() ?: return null
        if (!hasRole("BUYER")) return null
        val sub = requireNotNull(jwt.subject) { "JWT has no sub claim" }
        buyerRepository.findByCognitoSub(sub)?.let { return it }

        val (email, name) = fetchProfileFromCognito(sub)
        // Link an existing guest-checkout row by email if one exists (same
        // person checked out as a guest before creating an account) instead
        // of creating a duplicate.
        val existingByEmail = buyerRepository.findByEmailIgnoreCase(email)
        if (existingByEmail != null) {
            existingByEmail.cognitoSub = sub
            return buyerRepository.save(existingByEmail)
        }
        return buyerRepository.save(Buyer(name = name, email = email, cognitoSub = sub))
    }

    fun requireBuyer(): Buyer = buyerOrNull() ?: throw ForbiddenException("A buyer account is required for this action")

    /** Null until seller onboarding (POST /api/stores) creates the row — never JIT-created. */
    fun sellerOrNull(): Seller? {
        val jwt = jwtOrNull() ?: return null
        if (!hasRole("SELLER")) return null
        return sellerRepository.findByCognitoSub(requireNotNull(jwt.subject) { "JWT has no sub claim" })
    }

    fun requireSeller(): Seller = sellerOrNull() ?: throw ForbiddenException("A seller account is required for this action")

    /**
     * Used only by seller onboarding (POST /api/stores) — the one place
     * that operates on "any authenticated Cognito identity" regardless of
     * current group membership, since that call is what grants ROLE_SELLER
     * in the first place.
     */
    fun currentIdentityOrNull(): CognitoIdentity? {
        val jwt = jwtOrNull() ?: return null
        val sub = requireNotNull(jwt.subject) { "JWT has no sub claim" }
        val (email, name) = fetchProfileFromCognito(sub)
        return CognitoIdentity(sub = sub, email = email, name = name)
    }

    /** JIT-provisioned — safe because there's no public path into the Cognito `admin` group. Same read-only-transaction caveat as buyerOrNull — the caller must not be @Transactional(readOnly = true). */
    @Transactional
    fun adminOrNull(): Admin? {
        val jwt = jwtOrNull() ?: return null
        if (!hasRole("ADMIN")) return null
        val sub = requireNotNull(jwt.subject) { "JWT has no sub claim" }
        adminRepository.findByCognitoSub(sub)?.let { return it }

        val (email, name) = fetchProfileFromCognito(sub)
        return adminRepository.save(Admin(cognitoSub = sub, email = email, name = name))
    }

    fun requireAdmin(): Admin = adminOrNull() ?: throw ForbiddenException("An admin account is required for this action")
}

data class CognitoIdentity(val sub: String, val email: String, val name: String)
