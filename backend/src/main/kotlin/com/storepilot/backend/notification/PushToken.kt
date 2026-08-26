package com.storepilot.backend.notification

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.seller.Seller
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/**
 * One registered Expo push token for one of a seller's devices — a seller
 * can have several rows (phone + tablet, or a reinstall that got a new
 * token before the old one expired). `token` is globally unique (an Expo
 * push token identifies one Expo Go/standalone-app install, never shared
 * across sellers), so PushTokenRepository upserts on it rather than on
 * (seller, token). See ExpoPushNotificationService for how these get used,
 * and PushTokenService for registration/cleanup.
 */
@Entity
@Table(name = "push_tokens", uniqueConstraints = [UniqueConstraint(columnNames = ["token"])])
class PushToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    var seller: Seller,
    @Column(nullable = false)
    var token: String,
    /** "ios" | "android" — informational only today (e.g. for future platform-specific payload tweaks), not branched on yet. */
    @Column(nullable = false)
    var platform: String,
    /** Bumped on every re-registration (app foreground while signed in) — not currently used to prune stale tokens, but keeps that option open without another migration later. */
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),
) : BaseEntity()
