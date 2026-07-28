package com.storepilot.backend.store

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.seller.Seller
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Mirrors src/types/store.ts's StoreAddress — embedded, not its own table.
 * `state` is deliberately one generic "state/province" field (not a
 * separate district+province pair) so the same shape works for any
 * country's address model — Sri Lanka's district and Australia's state
 * both fit here. Valid options come from the `states` reference table (see
 * common/State.kt), not a hardcoded list.
 */
@Embeddable
class StoreAddress(
    @Column(nullable = false)
    var city: String,
    @Column(nullable = false)
    var state: String,
)

/**
 * Mirrors src/types/store.ts's Store. `joinedAt` in the frontend type maps to
 * BaseEntity.createdAt — no separate column. `productCount`/`reviewCount`/
 * `followerCount`/`rating` are denormalized counters, same as the mock
 * (recomputed on write, not derived at read time, to keep list queries fast).
 */
@Entity
@Table(name = "stores")
class Store(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    var seller: Seller,
    @Column(nullable = false, unique = true)
    var slug: String,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var tagline: String,
    @Column(nullable = false, columnDefinition = "text")
    var description: String,
    @Column(name = "logo_url", nullable = false)
    var logoUrl: String,
    @Column(name = "banner_url", nullable = false)
    var bannerUrl: String,
    @Column(nullable = false)
    var category: StoreCategory,
    @Embedded
    var address: StoreAddress,
    @Column(name = "whatsapp_number", nullable = false)
    var whatsappNumber: String,
    @Column(nullable = false)
    var rating: Double = 0.0,
    @Column(name = "review_count", nullable = false)
    var reviewCount: Int = 0,
    @Column(name = "product_count", nullable = false)
    var productCount: Int = 0,
    @Column(name = "is_verified", nullable = false)
    var isVerified: Boolean = false,
    @Column(name = "follower_count", nullable = false)
    var followerCount: Int = 0,
    @Column(name = "verification_status", nullable = false)
    var verificationStatus: StoreVerificationStatus = StoreVerificationStatus.PENDING,
    @Column(name = "facebook_url")
    var facebookUrl: String? = null,
    @Column(name = "instagram_url")
    var instagramUrl: String? = null,
    @Column(name = "tiktok_url")
    var tiktokUrl: String? = null,
) : BaseEntity()
