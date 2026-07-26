package com.islandcart.backend.product

fun Product.toResponse(): ProductResponse =
    ProductResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        name = name,
        slug = slug,
        description = description,
        images = images.map { ProductImageResponse(requireNotNull(it.id), it.url, it.alt) },
        category = category.wireValue,
        priceLkr = priceLkr,
        compareAtPriceLkr = compareAtPriceLkr,
        stockQuantity = stockQuantity,
        status = status.wireValue,
        sku = sku,
        rating = rating,
        reviewCount = reviewCount,
        createdAt = requireNotNull(createdAt),
        updatedAt = requireNotNull(updatedAt),
    )
