package com.storepilot.backend.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Not aws-profile-gated: Cognito is the authorization server in every
 * environment (local dev points at a real, free Cognito user pool — there's
 * no local/mock auth implementation, unlike the S3/SES swap-ins).
 */
@ConfigurationProperties(prefix = "cognito")
data class CognitoProperties(
    val region: String = "ap-south-1",
    val userPoolId: String = "",
    val clientId: String = "",
    /**
     * Google Hosted-UI federation — a separate, confidential app client
     * from clientId above (which drives the direct AdminInitiateAuth
     * email/password flow and has no secret). oauthRedirectUri must
     * exactly match a Callback URL registered on that app client.
     */
    val oauthDomain: String = "",
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val oauthRedirectUri: String = "http://localhost:8080/api/auth/google/callback",
)
