package com.storepilot.backend.notification

import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.payout.Payout
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** Push-only, same reasoning as MessagingNotifier — no existing email touchpoint for payouts to extend. */
@Component
class PayoutNotifier(
    private val pushNotificationService: PushNotificationService,
    private val pushTokenRepository: PushTokenRepository,
    private val platformConfigService: PlatformConfigService,
    private val sellerNotificationService: SellerNotificationService,
) {
    private val log = LoggerFactory.getLogger(PayoutNotifier::class.java)

    /** PayoutService.markPaid — admin confirms the bank transfer actually went out. */
    fun payoutMarkedPaid(payout: Payout) {
        val title = "Payout sent"
        val body = "Your payout of ${platformConfigService.current().currencyCode} ${formatMoney(payout.net)} has been paid out."
        val sellerId = payout.store.seller.id
        if (sellerId != null) {
            val tokens = pushTokenRepository.findBySellerId(sellerId).map { it.token }
            if (tokens.isNotEmpty()) {
                try {
                    pushNotificationService.send(tokens, title, body, data = mapOf("type" to "payout", "id" to payout.id.toString()))
                } catch (e: Exception) {
                    log.warn("Failed to send payout push to seller {} — not failing the triggering operation", sellerId, e)
                }
            }
        }
        sellerNotificationService.notify(payout.store.seller, SellerNotificationType.PAYOUT, title, body, payout.id)
    }

    /** [cents] is this codebase's storage unit — see Product.price's doc comment. */
    private fun formatMoney(cents: Int): String = BigDecimal(cents).movePointLeft(2).toPlainString()
}
