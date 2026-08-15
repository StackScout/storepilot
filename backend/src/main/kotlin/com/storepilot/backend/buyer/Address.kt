package com.storepilot.backend.buyer

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.ShippingDetails
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * One of a buyer's saved shipping addresses — replaces the old single
 * `Buyer.defaultShipping` embedded field (see V16__buyer_address_book.sql's
 * backfill). Has no relationship to `Order` at all: `Order.shipping` is its
 * own immutable snapshot of the same `ShippingDetails` shape, copied at
 * checkout time, never a reference to a row here — editing or deleting an
 * address has zero effect on any past order. Exactly one address per buyer
 * may have `isDefault = true`, enforced in AddressService rather than a DB
 * constraint (flipping the previous default off and a new one on isn't
 * expressible as a single-row check/unique constraint).
 */
@Entity
@Table(name = "addresses")
class Address(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    /** Optional free-text label (e.g. "Home"/"Work") — purely for the buyer's own reference, never validated. */
    var label: String? = null,
    @Embedded
    var shipping: ShippingDetails,
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
) : BaseEntity()
