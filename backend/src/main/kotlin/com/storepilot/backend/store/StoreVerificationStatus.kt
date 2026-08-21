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
    /** Seller-initiated closure (see StoreService.closeStore) — a terminal state, never re-opened. Search/getBySlug already only surface ACTIVE, so a closed store drops out of the public marketplace with no other code changes; its own identity fields (name/slug/description) stay untouched so past buyers' order history keeps showing a coherent store name. */
    CLOSED("closed"),
}

@Converter(autoApply = true)
class StoreVerificationStatusConverter :
    WireValueEnumConverter<StoreVerificationStatus>(StoreVerificationStatus.entries.toTypedArray())
