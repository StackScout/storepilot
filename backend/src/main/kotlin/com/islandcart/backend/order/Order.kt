package com.islandcart.backend.order

import com.islandcart.backend.buyer.Buyer
import com.islandcart.backend.common.BaseEntity
import com.islandcart.backend.common.ShippingDetails
import com.islandcart.backend.store.Store
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mirrors src/types/order.ts's Order. `storeName`/`storeSlug` are NOT
 * duplicated (unlike the mock) — derive from the `store` relation in the DTO
 * mapper. `items`/`timeline` are child entities (aggregate pattern: Order is
 * the aggregate root, neither child is queried independently — no separate
 * repository for either).
 */
@Entity
@Table(name = "orders")
class Order(
    @Column(name = "order_number", nullable = false, unique = true)
    var orderNumber: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    var items: MutableList<OrderItem> = mutableListOf(),
    @Column(name = "subtotal_lkr", nullable = false)
    var subtotalLkr: Int,
    @Column(name = "shipping_fee_lkr", nullable = false)
    var shippingFeeLkr: Int,
    @Column(name = "platform_fee_lkr", nullable = false)
    var platformFeeLkr: Int,
    @Column(name = "total_lkr", nullable = false)
    var totalLkr: Int,
    @Column(nullable = false)
    var status: OrderStatus = OrderStatus.PENDING,
    @Column(name = "payment_method", nullable = false)
    var paymentMethod: PaymentMethod,
    @Column(name = "payment_status", nullable = false)
    var paymentStatus: PaymentStatus,
    /** Set once the buyer uploads a bank-transfer proof-of-payment image; null for every other payment method. */
    @Column(name = "receipt_url")
    var receiptUrl: String? = null,
    /** Set by ReceiptReminderJob each time it emails a reminder; null means "never reminded". Never exposed in OrderResponse. */
    @Column(name = "last_reminder_sent_at")
    var lastReminderSentAt: Instant? = null,
    @Embedded
    var shipping: ShippingDetails,
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("timestamp asc")
    var timeline: MutableList<OrderTimelineEntry> = mutableListOf(),
    @Column(name = "buyer_email", nullable = false)
    var buyerEmail: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id")
    var buyer: Buyer? = null,
) : BaseEntity()

/**
 * Mirrors src/types/order.ts's OrderItem. `productId` is a **plain UUID
 * column, not a foreign key** — deliberately, so a product can be deleted
 * without breaking historical orders. Every other field is an immutable
 * price/name/image snapshot taken at order-creation time; never join back to
 * the live Product to render an order (see database-model.md#orderitem-embedded).
 */
@Entity
@Table(name = "order_items")
class OrderItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,
    @Column(name = "product_id", nullable = false)
    var productId: UUID,
    @Column(name = "product_name", nullable = false)
    var productName: String,
    @Column(name = "product_image_url", nullable = false)
    var productImageUrl: String,
    @Column(name = "unit_price_lkr", nullable = false)
    var unitPriceLkr: Int,
    @Column(nullable = false)
    var quantity: Int,
) : BaseEntity()

/** Mirrors src/types/order.ts's OrderTimelineEntry — append-only, never edited/removed once written. */
@Entity
@Table(name = "order_timeline_entries")
class OrderTimelineEntry(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: Order,
    @Column(nullable = false)
    var status: OrderStatus,
    @Column(nullable = false)
    var label: String,
    @Column(nullable = false)
    var timestamp: Instant,
    @Column(columnDefinition = "text")
    var note: String? = null,
) : BaseEntity()
