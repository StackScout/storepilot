package com.storepilot.backend.review

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.store.Store
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * A single review is either a product review (`productId` set — buyer
 * reviewed one specific product they bought) or a store review
 * (`productId` null — buyer reviewed their overall experience with the
 * store, gated on any delivered order or completed booking there, not tied
 * to one product). `storeId` is always set, even for a product review, so
 * both kinds share one table and one buyer/store uniqueness story instead
 * of two near-identical entities. Deliberately not aggregated together:
 * Product.rating only reflects that product's own reviews, Store.rating
 * only reflects direct store-level reviews — see ReviewService for the
 * recompute logic. One review per buyer per product, and separately one
 * per buyer per store — enforced by partial unique indexes in
 * V18__reviews.sql (a plain composite unique constraint can't express
 * "unique only when productId is null").
 */
@Entity
@Table(name = "reviews")
class Review(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(name = "product_id")
    var productId: UUID? = null,
    @Column(nullable = false)
    var rating: Int,
    @Column(columnDefinition = "text")
    var comment: String? = null,
) : BaseEntity()
