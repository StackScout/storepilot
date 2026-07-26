package com.islandcart.backend.store

fun Store.toResponse(): StoreResponse =
    StoreResponse(
        id = requireNotNull(id),
        slug = slug,
        name = name,
        tagline = tagline,
        description = description,
        logoUrl = logoUrl,
        bannerUrl = bannerUrl,
        category = category.wireValue,
        address = StoreAddressResponse(address.city, address.district, address.province),
        whatsappNumber = whatsappNumber,
        rating = rating,
        reviewCount = reviewCount,
        productCount = productCount,
        isVerified = isVerified,
        joinedAt = requireNotNull(createdAt),
        followerCount = followerCount,
        verificationStatus = verificationStatus.wireValue,
    )

fun StoreSettings.toResponse(): StoreSettingsResponse =
    StoreSettingsResponse(
        storeId = requireNotNull(store.id),
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        bankAccountName = bankAccountName,
        bankAccountNumber = bankAccountNumber,
        bankName = bankName,
        transactionFeePercent = transactionFeePercent,
        codEnabled = codEnabled,
        onlinePaymentEnabled = onlinePaymentEnabled,
        bankTransferEnabled = bankTransferEnabled,
        sellerType = sellerType.wireValue,
        nicNumber = nicNumber,
        businessRegistrationNumber = businessRegistrationNumber,
        rejectionReason = rejectionReason,
    )
