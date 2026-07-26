package com.islandcart.backend.buyer

import com.islandcart.backend.common.BaseEntity
import com.islandcart.backend.common.ShippingDetails
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Mirrors src/types/buyer.ts's Buyer. No password column yet — see
 * docs/gaps-and-assumptions.md's "Buyer accounts have no password" entry;
 * that's a deliberate, tracked gap to close before this holds anything
 * sensitive, not an oversight here.
 */
@Entity
@Table(name = "buyers")
class Buyer(
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false, unique = true)
    var email: String,
    var phone: String? = null,
    @Embedded
    var defaultShipping: ShippingDetails? = null,
) : BaseEntity()
