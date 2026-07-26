package com.islandcart.backend.order

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Local-disk storage — default/dev implementation, active whenever the
 * `aws` profile isn't. See ReceiptStorageService for the S3 alternative.
 */
@Service
@Profile("!aws")
class LocalReceiptStorageService(
    private val properties: ReceiptStorageProperties,
) : ReceiptStorageService {
    private val uploadDir: Path by lazy {
        Path.of(properties.uploadDir).toAbsolutePath().normalize().also { Files.createDirectories(it) }
    }

    override fun store(file: MultipartFile): String {
        validateReceiptFile(file)
        val filename = "${UUID.randomUUID()}${receiptFileExtension(file.contentType)}"
        val destination = uploadDir.resolve(filename)
        file.inputStream.use { input -> Files.copy(input, destination) }
        return "${properties.publicPath}/$filename"
    }

    /** Already a fetchable path — served directly by LocalReceiptResourceConfig's resource handler. */
    override fun resolveUrl(reference: String): String = reference
}
