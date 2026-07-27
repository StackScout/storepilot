package com.islandcart.backend.common.storage

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serves files uploaded via FileStorageService (seller documents, product
 * images, courier receipts) as plain static files off local disk. Only
 * relevant in the local/dev profile — under the aws profile these live in
 * S3 and are fetched via short-lived presigned URLs instead.
 */
@Configuration
@Profile("!aws")
class LocalFileResourceConfig(
    private val properties: FileStorageProperties,
) : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val uploadDir = Path.of(properties.uploadDir).toAbsolutePath().normalize()
        Files.createDirectories(uploadDir)
        registry.addResourceHandler("${properties.publicPath}/**")
            .addResourceLocations(uploadDir.toUri().toString())
    }
}
