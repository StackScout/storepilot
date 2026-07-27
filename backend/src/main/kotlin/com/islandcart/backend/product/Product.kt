package com.islandcart.backend.product

import com.islandcart.backend.common.BaseEntity
import com.islandcart.backend.store.Store
import com.islandcart.backend.store.StoreCategory
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * Mirrors src/types/product.ts's Product. `storeName`/`storeSlug` are NOT
 * duplicated here (unlike the frontend mock, which denormalizes them for a
 * flat JSON shape) — DTO mappers derive them from the `store` relation
 * instead. Slug is unique per-store (not globally), matching
 * docs/api-contracts.md's documented rule; `sku` is intentionally not
 * unique yet — see docs/roadmap.md's "Duplicate-SKU validation" gap, not
 * enforced here either, by design, until that product decision is made.
 */
@Entity
@Table(
    name = "products",
    uniqueConstraints = [UniqueConstraint(columnNames = ["store_id", "slug"])],
)
class Product(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var slug: String,
    @Column(nullable = false, columnDefinition = "text")
    var description: String,
    @Column(nullable = false)
    var category: StoreCategory,
    @Column(name = "price_lkr", nullable = false)
    var priceLkr: Int,
    @Column(name = "compare_at_price_lkr")
    var compareAtPriceLkr: Int? = null,
    @Column(name = "stock_quantity", nullable = false)
    var stockQuantity: Int,
    /** When false, stockQuantity is ignored — status is never auto-forced to out-of-stock and decrementStock skips this product. Forced false whenever the owning store has stockManagementEnabled = false, regardless of what's stored here — see ProductService. */
    @Column(name = "track_stock", nullable = false)
    var trackStock: Boolean = true,
    @Column(nullable = false)
    var status: ProductStatus,
    var sku: String? = null,
    @Column(nullable = false)
    var rating: Double = 0.0,
    @Column(name = "review_count", nullable = false)
    var reviewCount: Int = 0,
    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("createdAt asc")
    var images: MutableList<ProductImage> = mutableListOf(),
) : BaseEntity()

/** Mirrors src/types/product.ts's ProductImage — a child entity, not an @ElementCollection, so individual images can be reordered/removed later. */
@Entity
@Table(name = "product_images")
class ProductImage(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product,
    @Column(nullable = false)
    var url: String,
    @Column(nullable = false)
    var alt: String,
) : BaseEntity()
