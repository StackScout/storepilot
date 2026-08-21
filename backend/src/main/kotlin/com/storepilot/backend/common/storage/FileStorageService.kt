package com.storepilot.backend.common.storage

import org.springframework.web.multipart.MultipartFile

/**
 * General-purpose file upload storage, shared by every upload type that
 * isn't a bank-transfer receipt (see order.ReceiptStorageService for that
 * one, kept separate/untouched since it already works and has its own
 * fixed validation rules). Two implementations selected by Spring profile:
 * LocalFileStorageService (default, local disk) and S3FileStorageService
 * (@Profile("aws")) — same split as ReceiptStorageService.
 *
 * [subdir] partitions uploads by purpose (e.g. "seller-documents",
 * "product-images", "courier-receipts") so each ends up in its own local
 * folder / S3 key prefix instead of one flat bucket of files.
 */
interface FileStorageService {
    /**
     * Validates and saves the file, returning an internal reference — a
     * local implementation may return a fetchable path directly, but
     * callers must always go through resolveUrl() rather than assume that.
     */
    fun store(
        subdir: String,
        file: MultipartFile,
        allowedContentTypes: Set<String>,
        maxBytes: Long,
    ): String

    /**
     * Turns a stored reference back into a URL fetchable right now. Called
     * at read time, never cached/persisted — a remote implementation (S3)
     * needs a freshly-signed URL with its own expiry, not a fixed one.
     */
    fun resolveUrl(reference: String): String

    /**
     * Permanently removes the underlying file — used by account-deletion
     * flows (see SellerAccountService) so a document isn't just orphaned
     * once its DB pointer is nulled. A no-op (not an error) for an
     * already-absolute URL never actually stored here (e.g. seed-data
     * placeholders), same guard resolveUrl() already applies.
     */
    fun delete(reference: String)
}

fun validateUploadFile(file: MultipartFile, allowedContentTypes: Set<String>, maxBytes: Long) {
    require(!file.isEmpty) { "File is empty" }
    require(file.size <= maxBytes) { "File must be ${maxBytes / (1024 * 1024)}MB or smaller" }
    require(file.contentType in allowedContentTypes) { "Unsupported file type: ${file.contentType}" }
}

fun uploadFileExtension(contentType: String?): String = when (contentType) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    "application/pdf" -> ".pdf"
    else -> ""
}

/** Common validation presets, reused by every caller instead of restating magic numbers/sets inline. */
object FileUploadPolicies {
    val DOCUMENT_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
    const val DOCUMENT_MAX_BYTES = 5L * 1024 * 1024 // 5MB

    val IMAGE_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    const val IMAGE_MAX_BYTES = 5L * 1024 * 1024 // 5MB
}
