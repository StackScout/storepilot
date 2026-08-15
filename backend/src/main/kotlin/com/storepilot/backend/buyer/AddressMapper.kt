package com.storepilot.backend.buyer

import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.order.ShippingDetailsInput
import com.storepilot.backend.order.ShippingDetailsResponse

fun Address.toResponse(): AddressResponse =
    AddressResponse(
        id = requireNotNull(id),
        label = label,
        shipping = ShippingDetailsResponse(
            fullName = shipping.fullName,
            phone = shipping.phone,
            addressLine1 = shipping.addressLine1,
            city = shipping.city,
            state = shipping.state,
            postalCode = shipping.postalCode,
        ),
        isDefault = isDefault,
        createdAt = requireNotNull(createdAt),
    )

fun ShippingDetailsInput.toShippingDetails(): ShippingDetails =
    ShippingDetails(
        fullName = fullName,
        phone = phone,
        addressLine1 = addressLine1,
        city = city,
        state = state,
        postalCode = postalCode,
    )
