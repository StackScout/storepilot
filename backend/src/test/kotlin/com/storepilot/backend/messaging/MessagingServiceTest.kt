package com.storepilot.backend.messaging

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.MessagingNotifier
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAccessService
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreStaffMemberRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class MessagingServiceTest {
    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()
    private val storeRepository = mockk<StoreRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val messagingNotifier = mockk<MessagingNotifier>()
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>(relaxed = true)
    private val storeAccessService = StoreAccessService(currentActor, storeStaffMemberRepository)

    private val service = MessagingService(conversationRepository, messageRepository, storeRepository, currentActor, messagingNotifier, storeAccessService)

    private val storeId: UUID = UUID.randomUUID()
    private val sellerId: UUID = UUID.randomUUID()
    private val buyerId: UUID = UUID.randomUUID()
    private val otherBuyerId: UUID = UUID.randomUUID()

    private lateinit var seller: Seller
    private lateinit var store: Store
    private lateinit var buyer: Buyer
    private lateinit var conversation: Conversation
    private lateinit var conversationId: UUID

    @BeforeEach
    fun setUp() {
        seller = mockk()
        every { seller.id } returns sellerId

        store = mockk()
        every { store.id } returns storeId
        every { store.name } returns "Test Store"
        every { store.slug } returns "test-store"
        every { store.seller } returns seller

        buyer = mockk()
        every { buyer.id } returns buyerId
        every { buyer.name } returns "Jane Buyer"
        conversation = Conversation(store = store, buyer = buyer).apply {
            id = UUID.randomUUID()
            createdAt = Instant.now()
        }
        conversationId = requireNotNull(conversation.id)

        every { messagingNotifier.sellerMessageReceived(any(), any()) } just Runs
    }

    @Test
    fun `getOrCreateConversation returns the existing conversation without creating a new one`() {
        every { currentActor.requireBuyer() } returns buyer
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { conversationRepository.findByStoreIdAndBuyerId(storeId, buyerId) } returns conversation

        val response = service.getOrCreateConversation(storeId)

        assertEquals(conversation.id, response.id)
        assertEquals(0, response.unreadCount)
    }

    @Test
    fun `getOrCreateConversation creates a new conversation when none exists`() {
        every { currentActor.requireBuyer() } returns buyer
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { conversationRepository.findByStoreIdAndBuyerId(storeId, buyerId) } returns null
        val saved = slot<Conversation>()
        every { conversationRepository.save(capture(saved)) } answers {
            saved.captured.apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        }

        val response = service.getOrCreateConversation(storeId)

        assertEquals(storeId, response.storeId)
        assertEquals(buyerId, response.buyerId)
    }

    @Test
    fun `sendMessage from the buyer increments the seller's unread count, not the buyer's`() {
        every { currentActor.buyerOrNull() } returns buyer
        every { currentActor.sellerOrNull() } returns null
        every { conversationRepository.findById(conversationId) } returns Optional.of(conversation)
        every { messageRepository.save(any()) } answers {
            firstArg<Message>().apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        }
        every { conversationRepository.save(any()) } answers { firstArg() }

        val response = service.sendMessage(conversationId, SendMessageInput("Hi!"))

        assertEquals("buyer", response.senderType)
        assertEquals(1, conversation.sellerUnreadCount)
        assertEquals(0, conversation.buyerUnreadCount)
    }

    @Test
    fun `sendMessage from the seller increments the buyer's unread count`() {
        every { currentActor.buyerOrNull() } returns null
        every { currentActor.sellerOrNull() } returns seller
        every { conversationRepository.findById(conversationId) } returns Optional.of(conversation)
        every { messageRepository.save(any()) } answers {
            firstArg<Message>().apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        }
        every { conversationRepository.save(any()) } answers { firstArg() }

        val response = service.sendMessage(conversationId, SendMessageInput("Hello back"))

        assertEquals("seller", response.senderType)
        assertEquals(1, conversation.buyerUnreadCount)
        assertEquals(0, conversation.sellerUnreadCount)
    }

    @Test
    fun `sendMessage rejects a caller who is neither the conversation's buyer nor its store's seller`() {
        val strangerBuyer: Buyer = mockk()
        every { strangerBuyer.id } returns otherBuyerId
        every { currentActor.buyerOrNull() } returns strangerBuyer
        every { currentActor.sellerOrNull() } returns null
        every { conversationRepository.findById(conversationId) } returns Optional.of(conversation)

        assertThrows(ForbiddenException::class.java) {
            service.sendMessage(conversationId, SendMessageInput("sneaky"))
        }
    }

    @Test
    fun `listMessages resets only the calling side's unread count`() {
        conversation.buyerUnreadCount = 3
        conversation.sellerUnreadCount = 5
        every { currentActor.buyerOrNull() } returns buyer
        every { currentActor.sellerOrNull() } returns null
        every { conversationRepository.findById(conversationId) } returns Optional.of(conversation)
        every { conversationRepository.save(any()) } answers { firstArg() }
        every { messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId) } returns emptyList()

        service.listMessages(conversationId)

        assertEquals(0, conversation.buyerUnreadCount)
        assertEquals(5, conversation.sellerUnreadCount)
    }

    @Test
    fun `listStoreConversations rejects a seller who doesn't own the store`() {
        val otherSeller: Seller = mockk()
        every { otherSeller.id } returns UUID.randomUUID()
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { currentActor.requireSeller() } returns otherSeller

        assertThrows(com.storepilot.backend.common.ForbiddenException::class.java) {
            service.listStoreConversations(storeId, 0, 20)
        }
    }
}
