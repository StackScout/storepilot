package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

/** Buyer-side mirror of PushToken — see its doc comment, identical shape/reasoning with `buyer` instead of `seller`. */
@Entity
@Table(name = "buyer_push_tokens", uniqueConstraints = [UniqueConstraint(columnNames = ["token"])])
class BuyerPushToken(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @Column(nullable = false)
    var token: String,
    @Column(nullable = false)
    var platform: String,
    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),
) : BaseEntity()
