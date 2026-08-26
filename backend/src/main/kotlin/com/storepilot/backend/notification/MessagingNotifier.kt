package com.storepilot.backend.notification

import com.storepilot.backend.messaging.Conversation
import com.storepilot.backend.messaging.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Push-only — unlike every other X Notifier, there's no existing email
 * touchpoint for messaging (see docs/features's messaging notes: buyer/
 * seller both find out about a new message by polling the thread, or now,
 * by this). Only the seller side is notified: a buyer opens the app
 * knowing they just sent a message, but a seller could be anywhere.
 */
@Component
class MessagingNotifier(
    private val pushNotificationService: PushNotificationService,
    private val pushTokenRepository: PushTokenRepository,
) {
    private val log = LoggerFactory.getLogger(MessagingNotifier::class.java)

    fun sellerMessageReceived(conversation: Conversation, message: Message) {
        val sellerId = conversation.store.seller.id ?: return
        val tokens = pushTokenRepository.findBySellerId(sellerId).map { it.token }
        if (tokens.isEmpty()) return
        try {
            pushNotificationService.send(
                tokens,
                title = "New message from ${conversation.buyer.name}",
                body = message.body.take(120),
                data = mapOf("type" to "conversation", "id" to conversation.id.toString()),
            )
        } catch (e: Exception) {
            log.warn("Failed to send message push to seller {} — not failing the triggering operation", sellerId, e)
        }
    }
}
