package com.islandcart.backend.product

import com.islandcart.backend.common.storage.FileStorageService

/** Image URLs are resolved fresh on every call (never cached) — see FileStorageService.resolveUrl. */
fun Product.toResponse(fileStorageService: FileStorageService): ProductResponse =
    ProductResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        name = name,
        slug = slug,
        description = description,
        images = images.map { ProductImageResponse(requireNotNull(it.id), fileStorageService.resolveUrl(it.url), it.alt) },
        category = category.wireValue,
        priceLkr = priceLkr,
        compareAtPriceLkr = compareAtPriceLkr,
        stockQuantity = stockQuantity,
        trackStock = trackStock,
        status = status.wireValue,
        sku = sku,
        rating = rating,
        reviewCount = reviewCount,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
