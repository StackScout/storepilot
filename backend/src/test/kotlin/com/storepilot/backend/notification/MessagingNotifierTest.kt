package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.messaging.Conversation
import com.storepilot.backend.messaging.Message
import com.storepilot.backend.messaging.SenderType
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.store.Store
import com.storepilot.backend.store.StoreAddress
import com.storepilot.backend.store.StoreVerificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class MessagingNotifierTest {
    private val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
    private val pushTokenRepository = mockk<PushTokenRepository>()
    private val sellerNotificationService = mockk<SellerNotificationService>(relaxed = true)

    private val notifier = MessagingNotifier(pushNotificationService, pushTokenRepository, sellerNotificationService)

    private lateinit var seller: Seller
    private lateinit var store: Store
    private lateinit var buyer: Buyer
    private lateinit var conversation: Conversation

    @BeforeEach
    fun setUp() {
        seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        store = Store(
            seller = seller, slug = "store", name = "Handicrafts Store", tagline = "tagline", description = "description",
            category = "handicrafts", address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000", verificationStatus = StoreVerificationStatus.ACTIVE,
        ).apply { id = UUID.randomUUID() }
        buyer = Buyer(name = "Jane Buyer", email = "buyer@example.com").apply { id = UUID.randomUUID() }
        conversation = Conversation(store = store, buyer = buyer).apply { id = UUID.randomUUID() }
        every { pushTokenRepository.findBySellerId(any()) } returns emptyList()
    }

    private fun message(body: String) = Message(conversation = conversation, senderType = SenderType.BUYER, body = body).apply { id = UUID.randomUUID() }

    @Test
    fun `sellerMessageReceived pushes every registered device with a truncated preview`() {
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        val longBody = "x".repeat(200)

        notifier.sellerMessageReceived(conversation, message(longBody))

        verify {
            pushNotificationService.send(
                listOf("token-1"),
                match { it.contains("Jane Buyer") },
                match { it.length == 120 },
                mapOf("type" to "conversation", "id" to conversation.id.toString()),
            )
        }
    }

    @Test
    fun `sellerMessageReceived does nothing when the seller has no registered devices`() {
        notifier.sellerMessageReceived(conversation, message("Hi there"))
        verify(exactly = 0) { pushNotificationService.send(any(), any(), any(), any()) }
    }

    @Test
    fun `sellerMessageReceived swallows a push failure`() {
        every { pushTokenRepository.findBySellerId(seller.id!!) } returns listOf(PushToken(seller = seller, token = "token-1", platform = "ios").apply { id = UUID.randomUUID() })
        every { pushNotificationService.send(any(), any(), any(), any()) } throws RuntimeException("Expo is down")

        notifier.sellerMessageReceived(conversation, message("Hi there"))
    }
}
