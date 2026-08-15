package com.storepilot.backend.messaging

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

/** Shape matches src/types/messaging.ts's Conversation exactly. unreadCount is scoped to whichever side (buyer/seller) is calling — resolved server-side in the mapper, never both counts exposed to either party. */
data class ConversationResponse(
    val id: UUID,
    val storeId: UUID,
    val storeName: String,
    val storeSlug: String,
    val buyerId: UUID,
    val buyerName: String,
    val lastMessageAt: Instant?,
    val unreadCount: Int,
    val createdAt: Instant,
)

data class MessageResponse(
    val id: UUID,
    val conversationId: UUID,
    val senderType: String,
    val body: String,
    val createdAt: Instant,
)

data class SendMessageInput(
    @field:NotBlank(message = "Enter a message")
    val body: String,
)
