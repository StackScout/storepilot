package com.storepilot.backend.notification

import com.storepilot.backend.common.security.CurrentActor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** Buyer-side mirror of PushTokenService — see its doc comment. */
@Service
@Transactional
class BuyerPushTokenService(
    private val buyerPushTokenRepository: BuyerPushTokenRepository,
    private val currentActor: CurrentActor,
) {
    fun register(input: RegisterPushTokenInput) {
        val buyer = currentActor.requireBuyer()
        val existing = buyerPushTokenRepository.findByToken(input.token)
        if (existing != null) {
            existing.buyer = buyer
            existing.platform = input.platform
            existing.lastSeenAt = Instant.now()
            buyerPushTokenRepository.save(existing)
        } else {
            buyerPushTokenRepository.save(BuyerPushToken(buyer = buyer, token = input.token, platform = input.platform))
        }
    }

    fun unregister(input: UnregisterPushTokenInput) {
        buyerPushTokenRepository.deleteByToken(input.token)
    }
}
