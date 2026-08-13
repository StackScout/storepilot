package com.storepilot.backend.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Authorization matrix, two layers deep:
 *  1. Coarse, filter-chain-level role gates below (hasRole checks) — "is
 *     this caller a seller/admin at all".
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
class SecurityConfig {
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
                auth
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    // Full store settings (bank details, NIC/ABN, contact
                    // info, verification documents) is owner-only — must be
                    // matched before the broader GET /api/stores/** permitAll
                    // below, since Spring Security's authorizeHttpRequests is
                    // first-match-wins. Buyer-facing checkout/order pages use
                    // GET /api/stores/*/public-settings instead, which stays
                    // covered by that broader permitAll rule.
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/settings").hasRole("SELLER")
                    // Same first-match-wins reasoning as /settings above —
                    // a seller's own pending verification-change-request
                    // status is not public.
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/verification-change-requests/current").hasRole("SELLER")
                    // Public marketplace browsing.
                    .requestMatchers(HttpMethod.GET, "/api/stores/**", "/api/products/**").permitAll()
                    // DB-backed platform config + address state/province options
                    // — the frontend fetches these instead of baking country
                    // content into NEXT_PUBLIC_* build args (see PlatformConfigController).
                    .requestMatchers(HttpMethod.GET, "/api/platform-config", "/api/states").permitAll()
                    // Public ABR register data (see AbnLookupController) —
                    // used by the onboarding form (no session yet) and by
                    // /admin (already authenticated, but no extra gate needed).
                    .requestMatchers(HttpMethod.GET, "/api/abn-lookup/**").permitAll()
                    // Locally-served uploaded files (product images, receipts,
                    // seller verification documents under the !aws profile) —
                    // plain static-asset fetches, not API calls; product
                    // images specifically must be reachable by anonymous
                    // marketplace browsing. Under the aws profile this
                    // handler doesn't exist (S3 presigned URLs instead), so
                    // this rule is a no-op there.
                    .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                    // Guest checkout — "possession of the order ID (+ phone
                    // for lookup) is the credential" model; buyerId is
                    // always derived server-side from CurrentActor, never a
                    // client-supplied field (see OrderDtos.kt).
                    .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/orders/lookup", "/api/orders/*").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/orders/*/receipt", "/api/orders/*/cancel", "/api/orders/*/payhere-checkout", "/api/orders/*/stripe-checkout").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/payments/payhere/notify").permitAll()
                    // Signature-verified inside StripeWebhookService, not by auth here — see its doc comment.
                    .requestMatchers(HttpMethod.POST, "/api/payments/stripe/webhook").permitAll()
                    // Signature-verified inside SellerBillingWebhookService — see its doc comment.
                    .requestMatchers(HttpMethod.POST, "/api/billing/stripe/webhook").permitAll()
                    // Seller's own store/plan — must come before the broader
                    // /api/me/** buyer rule below (first-match-wins).
                    .requestMatchers(HttpMethod.GET, "/api/me/store").hasRole("SELLER")
                    .requestMatchers("/api/me/seller/**").hasRole("SELLER")
                    // Buyer's own profile/orders.
                    .requestMatchers("/api/me/**").hasRole("BUYER")
                    // Seller onboarding — any authenticated Cognito user;
                    // this call is what grants ROLE_SELLER (see
                    // StoreService.create / task "Seller onboarding").
                    .requestMatchers(HttpMethod.POST, "/api/stores").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/stores/*/settings", "/api/stores/*/profile").hasRole("SELLER")
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/stores/*/driver-licence-document",
                        "/api/stores/*/abn-document",
                        "/api/stores/*/nic-document",
                        "/api/stores/*/business-reg-document",
                        "/api/stores/*/logo",
                        "/api/stores/*/banner",
                        "/api/stores/*/verification-change-requests",
                    ).hasRole("SELLER")
                    .requestMatchers(HttpMethod.POST, "/api/stores/*/stripe-connect/onboard", "/api/stores/*/stripe-connect/refresh").hasRole("SELLER")
                    .requestMatchers(HttpMethod.POST, "/api/stores/*/products").hasRole("SELLER")
                    .requestMatchers(HttpMethod.PATCH, "/api/products/*").hasRole("SELLER")
                    .requestMatchers(HttpMethod.DELETE, "/api/products/*").hasRole("SELLER")
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/orders").hasRole("SELLER")
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/payouts", "/api/stores/*/payouts/*").hasRole("SELLER")
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/fee-collections", "/api/stores/*/fee-collections/*").hasRole("SELLER")
                    .requestMatchers(HttpMethod.GET, "/api/stores/*/stripe-settlements").hasRole("SELLER")
                    .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status").hasRole("SELLER")
                    .requestMatchers(HttpMethod.POST, "/api/orders/*/verify-bank-transfer").hasRole("SELLER")
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2
                    .bearerTokenResolver(CookieBearerTokenResolver())
                    .jwt { jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter) }
            }
        return http.build()
    }
}
