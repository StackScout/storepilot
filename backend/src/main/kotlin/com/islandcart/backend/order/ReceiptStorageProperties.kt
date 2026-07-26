package com.islandcart.backend.order

import org.springframework.boot.context.properties.ConfigurationProperties

/** Backs both ReceiptStorageService implementations — uploadDir/publicPath are local-only, s3BucketName is aws-profile-only. */
@ConfigurationProperties(prefix = "receipts")
data class ReceiptStorageProperties(
    val uploadDir: String = "uploads/receipts",
    val publicPath: String = "/uploads/receipts",
    /** Only read by S3ReceiptStorageService (aws profile). */
    val s3BucketName: String = "",
)
