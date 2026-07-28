package com.storepilot.backend.common.storage

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Local-disk storage — default/dev implementation, active whenever the
 * `aws` profile isn't. See FileStorageService for the S3 alternative.
 */
@Service
@Profile("!aws")
class LocalFileStorageService(
    private val properties: FileStorageProperties,
) : FileStorageService {
    override fun store(
        subdir: String,
        file: MultipartFile,
        allowedContentTypes: Set<String>,
        maxBytes: Long,
    ): String {
        validateUploadFile(file, allowedContentTypes, maxBytes)
        val dir = Path.of(properties.uploadDir, subdir).toAbsolutePath().normalize().also { Files.createDirectories(it) }
        val filename = "${UUID.randomUUID()}${uploadFileExtension(file.contentType)}"
        file.inputStream.use { input -> Files.copy(input, dir.resolve(filename)) }
        return "${properties.publicPath}/$subdir/$filename"
    }

    /**
     * Already a fetchable path — served directly by LocalFileResourceConfig's
     * resource handler. Returned as-is for an already-absolute URL too
     * (e.g. picsum.photos seed-data placeholders stored directly in
     * Product.images, never having gone through store()).
     */
    override fun resolveUrl(reference: String): String = reference
}
