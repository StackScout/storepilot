package com.islandcart.backend.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.ses.SesClient

/**
 * AWS SDK client beans — only registered under the aws profile, so the
 * default/dev profile never needs AWS credentials to start up. Credentials
 * come from the EC2 instance role via the SDK's default credential provider
 * chain — never configured here, never in application.yml.
 */
@Configuration
@Profile("aws")
class AwsClientConfig(
    private val awsProperties: AwsProperties,
) {
    @Bean
    fun s3Client(): S3Client = S3Client.builder().region(Region.of(awsProperties.region)).build()

    @Bean
    fun s3Presigner(): S3Presigner = S3Presigner.builder().region(Region.of(awsProperties.region)).build()

    @Bean
    fun sesClient(): SesClient = SesClient.builder().region(Region.of(awsProperties.region)).build()
}
