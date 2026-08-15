package com.storepilot.backend.buyer

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * Mirrors src/types/buyer.ts's Buyer. No password here — Cognito owns
 * credentials entirely. `cognitoSub` links this row to the Cognito identity
 * (the JWT's `sub` claim) and is null for a guest-checkout buyer who has
 * never created an account; nullable+unique, so multiple guest rows can
 * coexist while still preventing two accounts from claiming the same
 * Cognito identity. This row is a profile-data cache — `ROLE_BUYER`
 * authorization always comes from the JWT's `cognito:groups` claim, never
 * from this row's existence (see CurrentActor). Saved shipping addresses
 * live in the separate `Address` entity (a real address book, one buyer to
 * many addresses) — this class no longer embeds a single default address
 * directly, see V16__buyer_address_book.sql.
 */
@Entity
@Table(name = "buyers")
class Buyer(
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false, unique = true)
    var email: String,
    var phone: String? = null,
    @Column(name = "cognito_sub", unique = true)
    var cognitoSub: String? = null,
) : BaseEntity()
