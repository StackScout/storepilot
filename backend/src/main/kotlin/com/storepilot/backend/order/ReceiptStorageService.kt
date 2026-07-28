package com.storepilot.backend.order

import org.springframework.web.multipart.MultipartFile

private val ALLOWED_RECEIPT_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp", "application/pdf")
private const val MAX_RECEIPT_BYTES = 5 * 1024 * 1024 // 5MB

/**
 * Two implementations, selected by Spring profile: LocalReceiptStorageService
 * (default, local disk) and S3ReceiptStorageService (@Profile("aws")).
 */
interface ReceiptStorageService {
    /**
     * Validates and saves the file, returning an internal reference — a
     * local implementation may return a fetchable path directly, but
     * callers must always go through resolveUrl() rather than assume that.
     */
    fun store(file: MultipartFile): String

    /**
     * Turns a stored reference back into a URL fetchable right now. Called
     * at read time, never cached/persisted — a remote implementation (S3)
     * needs a freshly-signed URL with its own expiry, not a fixed one.
     */
    fun resolveUrl(reference: String): String
}

/** Shared validation — every implementation must reject the same file types/sizes. */
fun validateReceiptFile(file: MultipartFile) {
    require(!file.isEmpty) { "Receipt file is empty" }
    require(file.size <= MAX_RECEIPT_BYTES) { "Receipt file must be 5MB or smaller" }
    require(file.contentType in ALLOWED_RECEIPT_CONTENT_TYPES) {
        "Receipt must be a JPEG, PNG, WEBP image or a PDF"
    }
}

fun receiptFileExtension(contentType: String?): String = when (contentType) {
    "image/jpeg" -> ".jpg"
    "image/png" -> ".png"
    "image/webp" -> ".webp"
    else -> ".pdf"
}
