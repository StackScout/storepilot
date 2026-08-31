package com.storepilot.backend.store

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

/**
 * A short-lived, hashed-token invite (mirrors EmailVerificationCode's
 * "never store the raw secret" discipline) that lets a store owner bring
 * a new staff Seller onto their store without the owner ever choosing the
 * invitee's password — the invitee sets it themselves via acceptInvite.
 */
@Entity
@Table(name = "store_staff_invites")
class StoreStaffInvite(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(nullable = false)
    var email: String,
    @Column(nullable = false)
    var name: String,
    @Column(name = "token_hash", nullable = false, unique = true)
    var tokenHash: String,
    @Column(nullable = false)
    var status: StoreStaffInviteStatus = StoreStaffInviteStatus.PENDING,
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,
) : BaseEntity()
