package com.storepilot.backend.notification

import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import com.storepilot.backend.seller.Seller
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * Same `type` vocabulary as the push-notification `data.type` field already
 * sent alongside every seller push (see OrderNotifier/BookingNotifier/
 * ProductNotifier/MessagingNotifier/PayoutNotifier's sendPushToSeller) —
 * kept identical on purpose so a single client-side switch (see mobile's
 * routeForNotification) resolves a tap-through destination for both a push
 * notification and a row read from this table, from the same (type, id)
 * pair.
 */
enum class SellerNotificationType(override val wireValue: String) : WireValue {
    ORDER("order"),
    BOOKING("booking"),
    PRODUCT("product"),
    CONVERSATION("conversation"),
    PAYOUT("payout"),
}

@Converter(autoApply = true)
class SellerNotificationTypeConverter :
    WireValueEnumConverter<SellerNotificationType>(SellerNotificationType.entries.toTypedArray())

/**
 * A seller-facing notification-center entry — mirrors AdminNotification's
 * shape exactly, scoped to one seller instead of "any admin". Created
 * alongside (never instead of) the existing push notification at each of
 * the Notifier classes' seller-facing call sites — see
 * SellerNotificationService.notify's doc comment for which events these
 * are and why not every seller-facing event creates one (the scheduled
 * reminder jobs are deliberately excluded, to avoid duplicate rows piling
 * up for the same order/booking every time a reminder re-fires).
 */
@Entity
@Table(name = "seller_notifications")
class SellerNotification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    var seller: Seller,
    @Column(nullable = false)
    var type: SellerNotificationType,
    @Column(nullable = false, columnDefinition = "text")
    var title: String,
    @Column(nullable = false, columnDefinition = "text")
    var body: String,
    /** The order/booking/product/conversation/payout id this notification is about — paired with [type], resolved to a route client-side, same as a push notification's `data.id`. Null is valid (there's nothing sensible to deep-link a future notification type to). */
    @Column(name = "entity_id")
    var entityId: UUID? = null,
    @Column(nullable = false)
    var read: Boolean = false,
) : BaseEntity()
