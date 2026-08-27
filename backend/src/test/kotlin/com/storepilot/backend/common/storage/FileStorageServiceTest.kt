package com.storepilot.backend.common.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class FileStorageServiceTest {
    @Test
    fun `validateUploadFile rejects an empty file`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", ByteArray(0))
        assertThrows(IllegalArgumentException::class.java) {
            validateUploadFile(file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
        }
    }

    @Test
    fun `validateUploadFile rejects a file over the size limit`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", ByteArray(10))
        assertThrows(IllegalArgumentException::class.java) {
            validateUploadFile(file, FileUploadPolicies.IMAGE_CONTENT_TYPES, maxBytes = 5)
        }
    }

    @Test
    fun `validateUploadFile rejects an unsupported content type`() {
        val file = MockMultipartFile("file", "doc.txt", "text/plain", byteArrayOf(1))
        assertThrows(IllegalArgumentException::class.java) {
            validateUploadFile(file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
        }
    }

    @Test
    fun `validateUploadFile accepts a valid file`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        validateUploadFile(file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
    }

    @Test
    fun `uploadFileExtension maps every known content type`() {
        assertEquals(".jpg", uploadFileExtension("image/jpeg"))
        assertEquals(".png", uploadFileExtension("image/png"))
        assertEquals(".webp", uploadFileExtension("image/webp"))
        assertEquals(".pdf", uploadFileExtension("application/pdf"))
    }

    @Test
    fun `uploadFileExtension returns empty for an unknown or missing content type`() {
        assertEquals("", uploadFileExtension("application/octet-stream"))
        assertEquals("", uploadFileExtension(null))
    }
}
