package com.storepilot.backend.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class PageResponseTest {
    @Test
    fun `toPageResponse maps content and carries over pagination metadata`() {
        val page = PageImpl(listOf(1, 2, 3), PageRequest.of(0, 3), 10)

        val result = page.toPageResponse { it * 10 }

        assertEquals(listOf(10, 20, 30), result.content)
        assertEquals(0, result.page)
        assertEquals(3, result.size)
        assertEquals(10, result.totalElements)
        assertEquals(4, result.totalPages)
    }

    @Test
    fun `toPageResponse handles an empty page`() {
        val page = PageImpl(emptyList<Int>(), PageRequest.of(0, 10), 0)

        val result = page.toPageResponse { it }

        assertEquals(emptyList<Int>(), result.content)
        assertEquals(0, result.totalElements)
        assertEquals(0, result.totalPages)
    }
}
