package com.islandcart.backend.common

import com.islandcart.backend.order.ReceiptStorageProperties
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serves uploaded bank-transfer receipts (see LocalReceiptStorageService)
 * as plain static files off local disk. Only relevant in the local/dev
 * profile — under the aws profile, receipts live in S3 and are fetched via
 * short-lived presigned URLs instead (see S3ReceiptStorageService), so this
 * handler would just be pointing at an empty directory.
 */
@Configuration
@Profile("!aws")
class LocalReceiptResourceConfig(
    private val receiptStorageProperties: ReceiptStorageProperties,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadDir = Path.of(receiptStorageProperties.uploadDir).toAbsolutePath().normalize()
        Files.createDirectories(uploadDir)
        registry.addResourceHandler("${receiptStorageProperties.publicPath}/**")
            .addResourceLocations(uploadDir.toUri().toString())
    }
}
