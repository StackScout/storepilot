package com.storepilot.backend.product

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.store.Store
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
import java.time.Instant

/**
 * Mirrors src/types/product.ts's Product. `storeName`/`storeSlug` are NOT
 * duplicated here (unlike the frontend mock, which denormalizes them for a
 * flat JSON shape) — DTO mappers derive them from the `store` relation
 * instead. Slug and (non-null) SKU are each unique per-store (not
 * globally), matching docs/api-contracts.md's documented rule — SKU
 * uniqueness is enforced case-insensitively in ProductService
 * .requireUniqueSku, with this table's constraint as an exact-case
 * defense-in-depth backstop (see V15__unique_product_sku_per_store.sql).
 * SKU stays optional: Postgres treats every NULL as distinct, so any
 * number of products with no SKU coexist fine.
 */
@Entity
@Table(
    name = "products",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["store_id", "slug"]),
        UniqueConstraint(columnNames = ["store_id", "sku"]),
    ],
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
    /** Validated against the admin-managed categories table (common.Category) at write time — see CategoryRepository.requireCategory. Plain varchar, not an FK — see Category.kt's doc comment. */
    @Column(nullable = false)
    var category: String,
    /** Cents (the currency's smallest unit), not whole dollars — see currency.ts#formatCurrency. */
    @Column(nullable = false)
    var price: Int,
    @Column(name = "compare_at_price")
    var compareAtPrice: Int? = null,
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
    /** Null means not currently alerted. Set by LowStockAlertJob, reset back to null on any restock (see ProductService.update) so the alert can re-fire the next time stock drops low again. */
    @Column(name = "last_low_stock_alert_sent_at")
    var lastLowStockAlertSentAt: Instant? = null,
    /** Null means inherit the store's StoreSettings.defaultFulfillmentTimeHours/defaultDeliveryTimeHours — see OrderService.createOrder's resolution, which snapshots the resolved value onto Order.fulfillmentTimeHours/deliveryTimeHours at checkout time (never joined back to this row afterward, same "no live Product join for order data" rule OrderItem's doc comment already establishes — a product can be deleted without breaking a past order's reminder schedule). */
    @Column(name = "fulfillment_time_hours")
    var fulfillmentTimeHours: Int? = null,
    @Column(name = "delivery_time_hours")
    var deliveryTimeHours: Int? = null,
    @OneToMany(mappedBy = "product", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder asc")
    var images: MutableList<ProductImage> = mutableListOf(),
) : BaseEntity()

/**
 * Mirrors src/types/product.ts's ProductImage — a child entity, not an
 * @ElementCollection, so individual images can be reordered/removed later.
 * index 0 (lowest sortOrder) is the product's primary image — shown as the
 * thumbnail on cards, carts, order snapshots, etc. wherever only one image
 * fits — see ProductService.storeImages, which sets sortOrder from upload
 * order (deliberately explicit rather than relying on createdAt, which
 * isn't a reliable distinguisher between images uploaded in the same
 * request).
 */
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
    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,
) : BaseEntity()
