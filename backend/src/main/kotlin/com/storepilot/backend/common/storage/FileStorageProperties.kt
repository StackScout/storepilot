package com.storepilot.backend.common.storage

import org.springframework.boot.context.properties.ConfigurationProperties

/** Backs both FileStorageService implementations — uploadDir/publicPath are local-only, s3BucketName is aws-profile-only. */
@ConfigurationProperties(prefix = "file-storage")
data class FileStorageProperties(
    val uploadDir: String = "uploads",
    val publicPath: String = "/uploads",
    /** Only read by S3FileStorageService (aws profile). */
    val s3BucketName: String = "",
)
