package com.storepilot.backend.order

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.store.Store
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
    @Column(nullable = false)
    var subtotal: Int,
    @Column(name = "delivery_method", nullable = false)
    var deliveryMethod: DeliveryMethod = DeliveryMethod.SHIPPING,
    @Column(name = "shipping_fee", nullable = false)
    var shippingFee: Int,
    @Column(name = "platform_fee", nullable = false)
    var platformFee: Int,
    @Column(nullable = false)
    var total: Int,
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
    /** Set together when the seller marks the order shipped — both required at that transition, see OrderService.updateStatus. */
    @Column(name = "tracking_number")
    var trackingNumber: String? = null,
    @Column(name = "courier_service_name")
    var courierServiceName: String? = null,
    /** Optional — stored reference (local path or S3 key), resolved fresh at read time like receiptUrl. */
    @Column(name = "courier_receipt_url")
    var courierReceiptUrl: String? = null,
    /** Stripe PaymentIntent id, set once checkout.session.completed arrives — needed to issue a refund against the right connected-account charge later. See StripeService. */
    @Column(name = "stripe_payment_intent_id")
    var stripePaymentIntentId: String? = null,
    /** Immutable snapshot of the coupon applied at checkout (if any) — never re-resolved against the live Coupon row, same "freeze at creation time" principle as OrderItem's price snapshot. Null/0 means no coupon. */
    @Column(name = "coupon_code")
    var couponCode: String? = null,
    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Int = 0,
    /**
     * Both null unless the seller's StoreSettings.gstRegistered was true at
     * the moment this order was created — presence of these two fields
     * together is what makes an order confirmation render as an ATO tax
     * invoice rather than a plain receipt (see OrderNotifier.orderConfirmed
     * and OrderMapper). Snapshotted rather than resolved live from the
     * store's current settings, same "freeze at creation time" principle as
     * OrderItem's price snapshot — a seller's GST-registration status or
     * ABN could change after the sale, but a tax invoice must reflect their
     * status at the time of that specific sale, not today's.
     */
    @Column(name = "seller_abn")
    var sellerAbn: String? = null,
    /** Cents, same convention as every other money field — see Product.price's doc comment. Computed as total / 11 at order-creation time (AU retail prices are GST-inclusive by convention), never recomputed later. */
    @Column(name = "gst_amount")
    var gstAmount: Int? = null,
    /**
     * Resolved once at order-creation time from the slowest item's
     * Product.fulfillmentTimeHours (or the store's
     * StoreSettings.defaultFulfillmentTimeHours where a product has no
     * override) — see OrderService.createOrder. Snapshotted rather than
     * joined live at reminder-time for the same reason OrderItem's fields
     * are snapshots: a product can be deleted or its fulfillment promise
     * changed without silently altering an order already placed under the
     * old promise. The fulfillment deadline itself is createdAt +
     * this value, computed on read by OrderFulfillmentReminderJob — no
     * separate deadline column needed.
     */
    @Column(name = "fulfillment_time_hours", nullable = false)
    var fulfillmentTimeHours: Int,
    /** Same resolution/snapshot reasoning as fulfillmentTimeHours, but the matching deadline is shippedAt + this value (see OrderDeliveryReminderJob) — delivery can't be measured from createdAt since it hasn't shipped yet. */
    @Column(name = "delivery_time_hours", nullable = false)
    var deliveryTimeHours: Int,
    /** Set once, at the PENDING/CONFIRMED -> SHIPPED transition — see OrderService.updateStatus. Null until then; the delivery-time clock (deliveryTimeHours above) starts here, not at order creation. */
    @Column(name = "shipped_at")
    var shippedAt: Instant? = null,
    /** One-shot flags, same idempotency shape as lastReminderSentAt — see OrderFulfillmentReminderJob/OrderDeliveryReminderJob. */
    @Column(name = "fulfillment_reminder_sent_at")
    var fulfillmentReminderSentAt: Instant? = null,
    @Column(name = "fulfillment_overdue_reminder_sent_at")
    var fulfillmentOverdueReminderSentAt: Instant? = null,
    @Column(name = "delivery_reminder_sent_at")
    var deliveryReminderSentAt: Instant? = null,
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
    @Column(name = "unit_price", nullable = false)
    var unitPrice: Int,
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
