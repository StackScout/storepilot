package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/** Buyer-side mirror of SellerNotificationType — same (type, id) deep-link vocabulary, resolved to a route client-side by mobile's routeForNotification. */
enum class BuyerNotificationType(override val wireValue: String) : WireValue {
    ORDER("order"),
    BOOKING("booking"),
    CONVERSATION("conversation"),
}

@Converter(autoApply = true)
class BuyerNotificationTypeConverter :
    WireValueEnumConverter<BuyerNotificationType>(BuyerNotificationType.entries.toTypedArray())

/**
 * A buyer-facing notification-center entry — mirrors SellerNotification's
 * shape exactly, scoped to one buyer instead of one seller. Created
 * alongside (never instead of) the existing buyer email at each of
 * OrderNotifier/BookingNotifier's buyer-facing one-shot call sites (order
 * confirmed/shipped/delivered/cancelled, payment verified, booking
 * created/confirmed/cancelled/completed/no-show, return decided/refunded),
 * plus MessagingNotifier.buyerMessageReceived (push+in-app only, no email,
 * mirroring the seller side's sellerMessageReceived) —
 * see BuyerNotificationService.notify's doc comment. Reminder emails
 * (receiptReminder, bookingReminder) are deliberately excluded, same
 * reasoning as SellerNotificationService's own exclusion of the seller
 * reminder jobs.
 */
@Entity
@Table(name = "buyer_notifications")
class BuyerNotification(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @Column(nullable = false)
    var type: BuyerNotificationType,
    @Column(nullable = false, columnDefinition = "text")
    var title: String,
    @Column(nullable = false, columnDefinition = "text")
    var body: String,
    @Column(name = "entity_id")
    var entityId: UUID? = null,
    @Column(nullable = false)
    var read: Boolean = false,
) : BaseEntity()
