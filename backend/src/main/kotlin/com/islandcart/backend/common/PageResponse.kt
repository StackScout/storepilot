package com.islandcart.backend.common

import org.springframework.data.domain.Page

/** Generic paginated response wrapper — matches src/types' PageResponse<T> on the frontend. */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <T : Any, R> Page<T>.toPageResponse(mapper: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(mapper),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
