package com.storepilot.backend.notification

import com.storepilot.backend.messaging.Conversation
import com.storepilot.backend.messaging.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Push-only — unlike every other X Notifier, there's no existing email
 * touchpoint for messaging (see docs/features's messaging notes: buyer/
 * seller both find out about a new message by polling the thread, or now,
 * by this). Both directions are notified: [sellerMessageReceived] for a
 * buyer-sent message, [buyerMessageReceived] for a seller-sent one — this
 * used to be one-directional only (a seller could be anywhere, unlike a
 * buyer who just sent the message from inside the app), but now that
 * buyers have their own push/notification-center channel (BuyerPushToken/
 * BuyerNotificationService) there's no reason to leave them unnotified.
 */
@Component
class MessagingNotifier(
    private val pushNotificationService: PushNotificationService,
    private val pushTokenRepository: PushTokenRepository,
    private val sellerNotificationService: SellerNotificationService,
    private val buyerPushTokenRepository: BuyerPushTokenRepository,
    private val buyerNotificationService: BuyerNotificationService,
) {
    private val log = LoggerFactory.getLogger(MessagingNotifier::class.java)

    fun sellerMessageReceived(conversation: Conversation, message: Message) {
        val title = "New message from ${conversation.buyer.name}"
        val body = message.body.take(120)
        val sellerId = conversation.store.seller.id
        if (sellerId != null) {
            val tokens = pushTokenRepository.findBySellerId(sellerId).map { it.token }
            if (tokens.isNotEmpty()) {
                try {
                    pushNotificationService.send(tokens, title, body, data = mapOf("type" to "conversation", "id" to conversation.id.toString()))
                } catch (e: Exception) {
                    log.warn("Failed to send message push to seller {} — not failing the triggering operation", sellerId, e)
                }
            }
        }
        sellerNotificationService.notify(conversation.store.seller, SellerNotificationType.CONVERSATION, title, body, conversation.id)
    }

    fun buyerMessageReceived(conversation: Conversation, message: Message) {
        val title = "New message from ${conversation.store.name}"
        val body = message.body.take(120)
        val buyerId = conversation.buyer.id ?: return
        val tokens = buyerPushTokenRepository.findByBuyerId(buyerId).map { it.token }
        if (tokens.isNotEmpty()) {
            try {
                pushNotificationService.send(tokens, title, body, data = mapOf("type" to "conversation", "id" to conversation.id.toString()))
            } catch (e: Exception) {
                log.warn("Failed to send message push to buyer {} — not failing the triggering operation", buyerId, e)
            }
        }
        buyerNotificationService.notify(conversation.buyer, BuyerNotificationType.CONVERSATION, title, body, conversation.id)
    }
}
