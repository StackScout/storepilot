package com.storepilot.backend.seller

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * The account behind a Store (see Store.sellerId). Created explicitly during
 * seller onboarding (POST /api/stores) alongside the Cognito `seller` group
 * assignment — never JIT-provisioned like Buyer/Admin, since onboarding
 * already collects real business data a JIT row couldn't fabricate.
 * `cognitoSub` links this row to the Cognito identity (the JWT's `sub`
 * claim); this row is a profile-data cache only — `ROLE_SELLER`
 * authorization always comes from the JWT's `cognito:groups` claim, never
 * from this row's existence (see CurrentActor).
 */
@Entity
@Table(name = "sellers")
class Seller(
    @Column(name = "cognito_sub", nullable = false, unique = true)
    var cognitoSub: String,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
