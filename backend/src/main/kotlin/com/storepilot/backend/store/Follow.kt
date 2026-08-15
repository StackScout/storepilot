package com.storepilot.backend.store

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/** A buyer following a store — one row per (buyer, store) pair, enforced by a unique constraint. Store.followerCount is a denormalized counter kept in sync on follow/unfollow, same pattern as rating/reviewCount. */
@Entity
@Table(
    name = "follows",
    uniqueConstraints = [UniqueConstraint(columnNames = ["buyer_id", "store_id"])],
)
class Follow(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
) : BaseEntity()
