package com.storepilot.backend.store

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.seller.Seller
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Links a staff Seller to the one store they were invited into — additive
 * to Store.seller (which keeps meaning "the owner," untouched). A staff
 * Seller is a full Seller row (same Cognito "seller" group, same
 * CurrentActor.requireSeller() resolution) that simply owns no Store of
 * its own; see StoreAccessService for how the two are distinguished.
 */
@Entity
@Table(name = "store_staff_members")
class StoreStaffMember(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false, unique = true)
    var seller: Seller,
) : BaseEntity()
