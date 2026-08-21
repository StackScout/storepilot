package com.storepilot.backend.common.storage

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * S3-backed storage for the aws profile — see FileStorageService for the
 * local/dev alternative. The bucket stays private (uploads here include
 * driver's licence/ABN documents and bank-adjacent seller data), so
 * store() returns the object key and resolveUrl() mints a short-lived
 * presigned GET URL fresh on every read, same pattern as S3ReceiptStorageService.
 */
@Service
@Profile("aws")
class S3FileStorageService(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val properties: FileStorageProperties,
) : FileStorageService {
    override fun store(
        subdir: String,
        file: MultipartFile,
        allowedContentTypes: Set<String>,
        maxBytes: Long,
    ): String {
        validateUploadFile(file, allowedContentTypes, maxBytes)
        val key = "$subdir/${UUID.randomUUID()}${uploadFileExtension(file.contentType)}"
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

    /** An already-absolute URL (e.g. picsum.photos seed-data placeholders, never stored via store()) is returned as-is rather than treated as an S3 key. */
    override fun resolveUrl(reference: String): String {
        if (reference.startsWith("http://") || reference.startsWith("https://")) return reference
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .getObjectRequest(
                GetObjectRequest.builder().bucket(properties.s3BucketName).key(reference).build(),
            )
            .build()
        return s3Presigner.presignGetObject(presignRequest).url().toString()
    }

    override fun delete(reference: String) {
        if (reference.startsWith("http://") || reference.startsWith("https://")) return
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.s3BucketName).key(reference).build())
    }
}
