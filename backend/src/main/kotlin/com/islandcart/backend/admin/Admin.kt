package com.islandcart.backend.admin

import com.islandcart.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * JIT-provisioned on first authenticated request from a JWT whose
 * `cognito:groups` includes `admin` — safe because there is no public path
 * into that Cognito group (only granted out-of-band via console/CLI). This
 * row is a profile-data cache only (name/email for audit trails); it is
 * NEVER the authorization source of truth — `ROLE_ADMIN` always comes from
 * the JWT claim, re-checked every request (see CurrentActor). Removing
 * someone from the Cognito group revokes access immediately regardless of
 * whether this row still exists.
 */
@Entity
@Table(name = "admins")
class Admin(
    @Column(name = "cognito_sub", nullable = false, unique = true)
    var cognitoSub: String,
    @Column(nullable = false, unique = true)
    var email: String,
    @Column(nullable = false)
    var name: String,
) : BaseEntity()
