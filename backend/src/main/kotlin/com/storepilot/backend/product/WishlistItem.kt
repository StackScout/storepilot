package com.storepilot.backend.product

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** A buyer saving a product for later — one row per (buyer, product) pair, enforced by a unique constraint. Mirrors store/Follow.kt's shape exactly (product instead of store). */
@Entity
@Table(
    name = "wishlist_items",
    uniqueConstraints = [UniqueConstraint(columnNames = ["buyer_id", "product_id"])],
)
class WishlistItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product,
) : BaseEntity()
