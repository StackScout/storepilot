package com.islandcart.backend.common

import org.springframework.boot.context.properties.ConfigurationProperties

/** Shared AWS region for every AWS SDK client (S3, SES) — only meaningful under the aws profile. */
@ConfigurationProperties(prefix = "aws")
data class AwsProperties(
    val region: String = "us-east-1",
)
