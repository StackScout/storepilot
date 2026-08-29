package com.storepilot.backend.messaging

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.notification.MessagingNotifier
import com.storepilot.backend.store.StoreRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/**
 * One conversation per (store, buyer) pair — see Conversation's doc
 * comment. Every read/write here first resolves which side (buyer or
 * seller) the caller is relative to the conversation, via
 * requireParticipant — a conversation has exactly two participants and
 * nobody else may read or post to it, unlike the public "ID is proof
 * enough" model used for order/booking status pages.
 */
@Service
@Transactional(readOnly = true)
class MessagingService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val storeRepository: StoreRepository,
    private val currentActor: CurrentActor,
    private val messagingNotifier: MessagingNotifier,
) {
    /**
     * POST /api/stores/{storeId}/conversations — buyer-only, get-or-create.
     * Explicitly @Transactional (not the class default readOnly = true):
     * requireBuyer() may JIT-provision a new Buyer row on the caller's
     * first request, same caveat as everywhere else CurrentActor.buyerOrNull
     * is called for the first time in a request.
     */
    @Transactional
    fun getOrCreateConversation(storeId: UUID): ConversationResponse {
        val buyer = currentActor.requireBuyer()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val buyerId = requireNotNull(buyer.id)
        val existing = conversationRepository.findByStoreIdAndBuyerId(storeId, buyerId)
        val conversation = existing ?: conversationRepository.save(Conversation(store = store, buyer = buyer))
        return conversation.toResponse(SenderType.BUYER)
    }

    /** Unpaged — internal cross-service use (e.g. BuyerExportService's full data-export bundle). GET /api/me/conversations uses the paged overload below. */
    @Transactional
    fun listMyConversations(): List<ConversationResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        return conversationRepository.findByBuyerIdOrderByLastMessageAtDesc(buyerId).map { it.toResponse(SenderType.BUYER) }
    }

    /** GET /api/me/conversations — buyer-scoped list, newest activity first. */
    @Transactional
    fun listMyConversations(page: Int, size: Int): PageResponse<ConversationResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return conversationRepository.findByBuyerIdOrderByLastMessageAtDesc(buyerId, pageable).toPageResponse { it.toResponse(SenderType.BUYER) }
    }

    /** GET /api/stores/{storeId}/conversations — seller-scoped list. */
    fun listStoreConversations(storeId: UUID, page: Int, size: Int): PageResponse<ConversationResponse> {
        requireOwnedStore(storeId)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return conversationRepository.findByStoreIdOrderByLastMessageAtDesc(storeId, pageable).toPageResponse { it.toResponse(SenderType.SELLER) }
    }

    fun getById(id: UUID): ConversationResponse {
        val conversation = requireConversation(id)
        val side = requireParticipant(conversation)
        return conversation.toResponse(side)
    }

    /** Unpaged — internal cross-service use (e.g. BuyerExportService's full data-export bundle). GET /api/conversations/{id}/messages uses the paged overload below. Also marks every message as read for the calling side, same as the paged overload. */
    @Transactional
    fun listMessages(id: UUID): List<MessageResponse> {
        val conversation = requireConversation(id)
        val side = requireParticipant(conversation)
        if (side == SenderType.BUYER) conversation.buyerUnreadCount = 0 else conversation.sellerUnreadCount = 0
        conversationRepository.save(conversation)
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(id).map { it.toResponse() }
    }

    /** GET /api/conversations/{id}/messages — also marks every message as read for the calling side. */
    @Transactional
    fun listMessages(id: UUID, page: Int, size: Int): PageResponse<MessageResponse> {
        val conversation = requireConversation(id)
        val side = requireParticipant(conversation)
        if (side == SenderType.BUYER) conversation.buyerUnreadCount = 0 else conversation.sellerUnreadCount = 0
        conversationRepository.save(conversation)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(id, pageable).toPageResponse { it.toResponse() }
    }

    /** POST /api/conversations/{id}/messages */
    @Transactional
    fun sendMessage(id: UUID, input: SendMessageInput): MessageResponse {
        val conversation = requireConversation(id)
        val side = requireParticipant(conversation)
        val now = Instant.now()
        val message = messageRepository.save(Message(conversation = conversation, senderType = side, body = input.body.trim()))
        conversation.lastMessageAt = now
        if (side == SenderType.BUYER) conversation.sellerUnreadCount += 1 else conversation.buyerUnreadCount += 1
        conversationRepository.save(conversation)
        if (side == SenderType.BUYER) messagingNotifier.sellerMessageReceived(conversation, message)
        return message.toResponse()
    }

    private fun requireConversation(id: UUID): Conversation =
        conversationRepository.findById(id).orElseThrow { NotFoundException("Conversation $id not found") }

    /** Which side [currentActor] is relative to [conversation] — throws if neither. */
    private fun requireParticipant(conversation: Conversation): SenderType {
        val buyer = currentActor.buyerOrNull()
        if (buyer != null && buyer.id == conversation.buyer.id) return SenderType.BUYER
        val seller = currentActor.sellerOrNull()
        if (seller != null && seller.id == conversation.store.seller.id) return SenderType.SELLER
        throw ForbiddenException("You aren't a participant in conversation ${conversation.id}")
    }

    private fun requireOwnedStore(storeId: UUID) {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
    }
}
