package com.islandcart.backend.common.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient

/**
 * Not @Profile("aws") — unlike AwsClientConfig's S3/SES clients, Cognito is
 * used in every environment. Credentials come from the SDK's default
 * provider chain (EC2 instance role in production; a local AWS profile with
 * Cognito permissions, e.g. AWS_PROFILE=islandcart-dev, for local dev) —
 * never configured here.
 */
@Configuration
class CognitoClientConfig(
    private val cognitoProperties: CognitoProperties,
) {
    @Bean
    fun cognitoIdentityProviderClient(): CognitoIdentityProviderClient =
        CognitoIdentityProviderClient.builder().region(Region.of(cognitoProperties.region)).build()
}
