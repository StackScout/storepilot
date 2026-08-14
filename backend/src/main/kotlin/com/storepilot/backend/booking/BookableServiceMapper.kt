package com.storepilot.backend.booking

import com.storepilot.backend.common.storage.FileStorageService

/** Image URLs are resolved fresh on every call (never cached) — see FileStorageService.resolveUrl. */
fun BookableService.toResponse(fileStorageService: FileStorageService): BookableServiceResponse =
    BookableServiceResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        name = name,
        slug = slug,
        description = description,
        images = images.map { BookableServiceImageResponse(requireNotNull(it.id), fileStorageService.resolveUrl(it.url), it.alt) },
        category = category.wireValue,
        price = price,
        durationMinutes = durationMinutes,
        bufferMinutes = bufferMinutes,
        status = status.wireValue,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
