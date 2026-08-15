package com.storepilot.backend.messaging

/** [viewerSide] picks which of buyerUnreadCount/sellerUnreadCount is exposed as unreadCount — the viewer's own unread count, never the other side's. */
fun Conversation.toResponse(viewerSide: SenderType): ConversationResponse =
    ConversationResponse(
        id = requireNotNull(id),
        storeId = requireNotNull(store.id),
        storeName = store.name,
        storeSlug = store.slug,
        buyerId = requireNotNull(buyer.id),
        buyerName = buyer.name,
        lastMessageAt = lastMessageAt,
        unreadCount = if (viewerSide == SenderType.BUYER) buyerUnreadCount else sellerUnreadCount,
        createdAt = requireNotNull(createdAt),
    )

fun Message.toResponse(): MessageResponse =
    MessageResponse(
        id = requireNotNull(id),
        conversationId = requireNotNull(conversation.id),
        senderType = senderType.wireValue,
        body = body,
        createdAt = requireNotNull(createdAt),
    )
