package com.storepilot.backend.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PushTokenRepository : JpaRepository<PushToken, UUID> {
    fun findByToken(token: String): PushToken?

    /** The recipient list for every seller-facing push send — see e.g. BookingNotifier.sellerBookingCreated. */
    fun findBySellerId(sellerId: UUID): List<PushToken>

    fun deleteByToken(token: String)
}
