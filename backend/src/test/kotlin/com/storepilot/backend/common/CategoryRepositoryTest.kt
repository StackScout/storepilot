package com.storepilot.backend.common

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CategoryRepositoryTest {
    private val categoryRepository = mockk<CategoryRepository>()

    @Test
    fun `requireCategory returns the wire value when it exists`() {
        every { categoryRepository.existsByWireValue("handicrafts") } returns true

        assertEquals("handicrafts", categoryRepository.requireCategory("handicrafts"))
    }

    @Test
    fun `requireCategory throws for an unknown wire value`() {
        every { categoryRepository.existsByWireValue("not-a-category") } returns false

        val ex = assertThrows(IllegalArgumentException::class.java) { categoryRepository.requireCategory("not-a-category") }
        assertEquals("Invalid category \"not-a-category\"", ex.message)
    }
}
