package com.storepilot.backend.messaging

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.BaseEntity
import com.storepilot.backend.common.WireValue
import com.storepilot.backend.common.WireValueEnumConverter
import com.storepilot.backend.store.Store
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Converter
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant

/**
 * One thread per (store, buyer) pair — not per-order/booking. Simpler than
 * per-order threads and matches the "message the seller" mental model most
 * marketplaces use; a buyer with multiple orders from the same store still
 * has just one conversation. Unread counts are per-side denormalized
 * counters (same recompute-inline-with-the-write principle as
 * Store.followerCount) rather than a query over unread messages, since
 * they're read on every conversation-list render.
 */
@Entity
@Table(name = "conversations")
class Conversation(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    var store: Store,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    var buyer: Buyer,
    @Column(name = "last_message_at")
    var lastMessageAt: Instant? = null,
    @Column(name = "buyer_unread_count", nullable = false)
    var buyerUnreadCount: Int = 0,
    @Column(name = "seller_unread_count", nullable = false)
    var sellerUnreadCount: Int = 0,
    @OneToMany(mappedBy = "conversation", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("createdAt asc")
    var messages: MutableList<Message> = mutableListOf(),
) : BaseEntity()

@Entity
@Table(name = "messages")
class Message(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    var conversation: Conversation,
    @Column(name = "sender_type", nullable = false)
    var senderType: SenderType,
    @Column(nullable = false, columnDefinition = "text")
    var body: String,
) : BaseEntity()

enum class SenderType(override val wireValue: String) : WireValue {
    BUYER("buyer"),
    SELLER("seller"),
}

@Converter(autoApply = true)
class SenderTypeConverter : WireValueEnumConverter<SenderType>(SenderType.entries.toTypedArray())
