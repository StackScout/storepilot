package com.storepilot.backend.store

import com.storepilot.backend.common.storage.FileStorageService

/** logoUrl/bannerUrl are resolved fresh on every call when present (never cached) — see FileStorageService.resolveUrl. Null means the seller hasn't uploaded one yet; the frontend renders a generated fallback in that case. */
fun Store.toResponse(fileStorageService: FileStorageService): StoreResponse =
    StoreResponse(
        id = requireNotNull(id),
        slug = slug,
        name = name,
        tagline = tagline,
        description = description,
        logoUrl = logoUrl?.let { fileStorageService.resolveUrl(it) },
        bannerUrl = bannerUrl?.let { fileStorageService.resolveUrl(it) },
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
        bookingsEnabled = bookingsEnabled,
    )

/** currentSettings is read fresh at map time (see StoreVerificationChangeRequestResponse's doc comment), not stored on the request row — null only if a store somehow has no settings row at all, defensively defaulted to INDIVIDUAL/blank rather than crashing the admin review list over one bad row. */
fun StoreVerificationChangeRequest.toResponse(currentSettings: StoreSettings?, fileStorageService: FileStorageService): StoreVerificationChangeRequestResponse =
    StoreVerificationChangeRequestResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        status = status.wireValue,
        sellerType = sellerType?.wireValue,
        driverLicenceNumber = driverLicenceNumber,
        abn = abn,
        nicNumber = nicNumber,
        businessRegistrationNumber = businessRegistrationNumber,
        driverLicenceDocumentUrl = driverLicenceDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        abnDocumentUrl = abnDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        nicDocumentUrl = nicDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        businessRegDocumentUrl = businessRegDocumentUrl?.let { fileStorageService.resolveUrl(it) },
        currentSellerType = currentSettings?.sellerType?.wireValue ?: SellerType.INDIVIDUAL.wireValue,
        currentDriverLicenceNumber = currentSettings?.driverLicenceNumber,
        currentAbn = currentSettings?.abn,
        currentNicNumber = currentSettings?.nicNumber,
        currentBusinessRegistrationNumber = currentSettings?.businessRegistrationNumber,
        rejectionReason = rejectionReason,
        submittedAt = requireNotNull(createdAt),
        reviewedAt = reviewedAt,
        reviewedByEmail = reviewedByEmail,
    )

/** Buyer-safe projection — see StorePublicSettingsResponse's doc comment. */
fun StoreSettings.toPublicResponse(): StorePublicSettingsResponse =
    StorePublicSettingsResponse(
        storeId = requireNotNull(store.id),
        bankAccountName = bankAccountName,
        bankAccountNumber = bankAccountNumber,
        bankName = bankName,
        codEnabled = codEnabled,
        onlinePaymentEnabled = onlinePaymentEnabled,
        bankTransferEnabled = bankTransferEnabled,
        pickupEnabled = pickupEnabled,
        stripeEnabled = stripeEnabled,
        stripeChargesEnabled = stripeChargesEnabled,
        bookingsEnabled = bookingsEnabled,
    )
