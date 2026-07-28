package com.storepilot.backend.order

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * S3-backed storage for the aws profile — see ReceiptStorageService for the
 * local/dev alternative. The bucket stays private (a receipt image can show
 * a buyer's bank account details): store() returns the object key, and
 * resolveUrl() mints a short-lived presigned GET URL fresh on every read
 * rather than a URL that could be persisted and later expire silently.
 */
@Service
@Profile("aws")
class S3ReceiptStorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: ReceiptStorageProperties,
) : ReceiptStorageService {
    override fun store(file: MultipartFile): String {
        validateReceiptFile(file)
        val key = "receipts/${UUID.randomUUID()}${receiptFileExtension(file.contentType)}"
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.s3BucketName)
                .key(key)
                .contentType(file.contentType)
                .build(),
            RequestBody.fromInputStream(file.inputStream, file.size),
        )
        return key
    }

    override fun resolveUrl(reference: String): String {
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(
                GetObjectRequest.builder().bucket(properties.s3BucketName).key(reference).build(),
            )
            .build()
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }
}
