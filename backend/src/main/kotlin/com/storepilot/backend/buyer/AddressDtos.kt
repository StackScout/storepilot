package com.storepilot.backend.buyer

import com.storepilot.backend.order.ShippingDetailsInput
import com.storepilot.backend.order.ShippingDetailsResponse
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID

data class AddressResponse(
    val id: UUID,
    val label: String?,
    val shipping: ShippingDetailsResponse,
    val isDefault: Boolean,
    val createdAt: Instant,
)

/** [isDefault]: true explicitly marks this the new default; a buyer's very first saved address becomes default regardless of this flag — see AddressService.create. */
data class AddressInput(
    val label: String? = null,
    @field:Valid
    val shipping: ShippingDetailsInput,
    val isDefault: Boolean = false,
)
