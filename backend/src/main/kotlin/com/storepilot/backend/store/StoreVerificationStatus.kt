package com.storepilot.backend.store

import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Converter

/**
 * Gates whether a store is discoverable/orderable on the public marketplace.
 * Set only via the admin approval workflow, never by the seller — see
 * docs/features/seller-auth.md#admin-not-a-real-role.
 */
enum class StoreVerificationStatus(override val wireValue: String) : WireValue {
    PENDING("pending"),
    ACTIVE("active"),
    REJECTED("rejected"),
}

@Converter(autoApply = true)
class StoreVerificationStatusConverter :
    WireValueEnumConverter<StoreVerificationStatus>(StoreVerificationStatus.entries.toTypedArray())
