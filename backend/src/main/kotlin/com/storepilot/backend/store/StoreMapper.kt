package com.storepilot.backend.store

import com.storepilot.backend.common.storage.FileStorageService

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
        address = StoreAddressResponse(address.city, address.state),
        whatsappNumber = whatsappNumber,
        rating = rating,
        reviewCount = reviewCount,
        productCount = productCount,
        isVerified = isVerified,
        joinedAt = requireNotNull(createdAt),
        followerCount = followerCount,
        verificationStatus = verificationStatus.wireValue,
        facebookUrl = facebookUrl,
        instagramUrl = instagramUrl,
        tiktokUrl = tiktokUrl,
    )

/** driverLicenceDocumentUrl/abnDocumentUrl are resolved fresh on every call (never cached) — see FileStorageService.resolveUrl. */
fun StoreSettings.toResponse(fileStorageService: FileStorageService): StoreSettingsResponse =
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
        driverLicenceNumber = driverLicenceNumber,
        abn = abn,
        nicNumber = nicNumber,
        businessRegistrationNumber = businessRegistrationNumber,
        rejectionReason = rejectionReason,
        driverLicenceDocumentUrl = driverLicenceDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        abnDocumentUrl = abnDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        nicDocumentUrl = nicDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        businessRegDocumentUrl = businessRegDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        stockManagementEnabled = stockManagementEnabled,
        pickupEnabled = pickupEnabled,
        stripeAccountId = stripeAccountId,
        stripeChargesEnabled = stripeChargesEnabled,
        stripePayoutsEnabled = stripePayoutsEnabled,
        stripeEnabled = stripeEnabled,
    )
