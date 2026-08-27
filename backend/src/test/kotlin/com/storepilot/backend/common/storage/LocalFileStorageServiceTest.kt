package com.storepilot.backend.common.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path

class LocalFileStorageServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private fun service() = LocalFileStorageService(FileStorageProperties(uploadDir = tempDir.toString(), publicPath = "/uploads"))

    @Test
    fun `store validates the file, saves it under the subdir, and returns a public path`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))

        val reference = service().store("product-images", file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)

        assertTrue(reference.startsWith("/uploads/product-images/"))
        assertTrue(reference.endsWith(".jpg"))
        val savedFile = tempDir.resolve("product-images").resolve(reference.removePrefix("/uploads/product-images/"))
        assertTrue(Files.exists(savedFile))
        assertEquals(listOf<Byte>(1, 2, 3), Files.readAllBytes(savedFile).toList())
    }

    @Test
    fun `store rejects a file that fails validation before touching the disk`() {
        val file = MockMultipartFile("file", "doc.txt", "text/plain", byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            service().store("product-images", file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
        }
        assertFalse(Files.exists(tempDir.resolve("product-images")))
    }

    @Test
    fun `resolveUrl returns the stored reference unchanged`() {
        assertEquals("/uploads/product-images/photo.jpg", service().resolveUrl("/uploads/product-images/photo.jpg"))
    }

    @Test
    fun `resolveUrl returns an absolute seed-data URL unchanged`() {
        assertEquals("https://picsum.photos/seed/1/400", service().resolveUrl("https://picsum.photos/seed/1/400"))
    }

    @Test
    fun `delete removes the underlying file`() {
        val svc = service()
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val reference = svc.store("product-images", file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
        val savedFile = tempDir.resolve("product-images").resolve(reference.removePrefix("/uploads/product-images/"))
        assertTrue(Files.exists(savedFile))

        svc.delete(reference)

        assertFalse(Files.exists(savedFile))
    }

    @Test
    fun `delete is a no-op for an absolute URL never stored locally`() {
        service().delete("https://picsum.photos/seed/1/400")
    }

    @Test
    fun `delete tolerates a reference that was never actually saved`() {
        service().delete("/uploads/product-images/does-not-exist.jpg")
    }
}
