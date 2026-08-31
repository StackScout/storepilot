package com.storepilot.backend.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuyerPushTokenRepository : JpaRepository<BuyerPushToken, UUID> {
    fun findByToken(token: String): BuyerPushToken?

    /** The recipient list for every buyer-facing push send — see e.g. OrderNotifier.orderShipped. */
    fun findByBuyerId(buyerId: UUID): List<BuyerPushToken>

    fun deleteByToken(token: String)
}
