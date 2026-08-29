package com.storepilot.backend.common.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.net.URI

class S3FileStorageServiceTest {
    private val s3Client = mockk<S3Client>()
    private val s3Presigner = mockk<S3Presigner>()
    private val properties = FileStorageProperties(s3BucketName = "storepilot-uploads")

    private val service = S3FileStorageService(s3Client, s3Presigner, properties)

    @Test
    fun `store uploads to the configured bucket under the subdir and returns the object key`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", byteArrayOf(1, 2, 3))
        val requestSlot = slot<PutObjectRequest>()
        every { s3Client.putObject(capture(requestSlot), any<software.amazon.awssdk.core.sync.RequestBody>()) } returns mockk(relaxed = true)

        val key = service.store("product-images", file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)

        assertTrue(key.startsWith("product-images/"))
        assertTrue(key.endsWith(".jpg"))
        assertEquals("storepilot-uploads", requestSlot.captured.bucket())
        assertEquals(key, requestSlot.captured.key())
    }

    @Test
    fun `store rejects an invalid file before ever calling S3`() {
        val file = MockMultipartFile("file", "doc.txt", "text/plain", byteArrayOf(1))

        assertThrows(IllegalArgumentException::class.java) {
            service.store("product-images", file, FileUploadPolicies.IMAGE_CONTENT_TYPES, FileUploadPolicies.IMAGE_MAX_BYTES)
        }
        verify(exactly = 0) { s3Client.putObject(any<PutObjectRequest>(), any<software.amazon.awssdk.core.sync.RequestBody>()) }
    }

    @Test
    fun `resolveUrl mints a presigned URL for a stored key`() {
        val presigned = mockk<PresignedGetObjectRequest>()
        every { presigned.url() } returns URI.create("https://storepilot-uploads.s3.amazonaws.com/product-images/photo.jpg?X-Amz-Signature=abc").toURL()
        every { s3Presigner.presignGetObject(any<GetObjectPresignRequest>()) } returns presigned

        val url = service.resolveUrl("product-images/photo.jpg")

        assertTrue(url.contains("X-Amz-Signature"))
    }

    @Test
    fun `resolveUrl returns an absolute seed-data URL unchanged without presigning`() {
        val url = service.resolveUrl("https://picsum.photos/seed/1/400")

        assertEquals("https://picsum.photos/seed/1/400", url)
        verify(exactly = 0) { s3Presigner.presignGetObject(any<GetObjectPresignRequest>()) }
    }

    @Test
    fun `delete removes the object from the bucket`() {
        val requestSlot = slot<DeleteObjectRequest>()
        every { s3Client.deleteObject(capture(requestSlot)) } returns mockk(relaxed = true)

        service.delete("product-images/photo.jpg")

        assertEquals("storepilot-uploads", requestSlot.captured.bucket())
        assertEquals("product-images/photo.jpg", requestSlot.captured.key())
    }

    @Test
    fun `delete is a no-op for an absolute URL never stored in S3`() {
        service.delete("https://picsum.photos/seed/1/400")

        verify(exactly = 0) { s3Client.deleteObject(any<DeleteObjectRequest>()) }
    }
}
