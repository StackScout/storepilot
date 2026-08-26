package com.storepilot.backend.notification

import com.storepilot.backend.common.security.CurrentActor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class PushTokenService(
    private val pushTokenRepository: PushTokenRepository,
    private val currentActor: CurrentActor,
) {
    /** Called on sign-in and on every app-foreground while signed in — upserts by token so a reinstall/re-registration never creates a duplicate row. */
    fun register(input: RegisterPushTokenInput) {
        val seller = currentActor.requireSeller()
        val existing = pushTokenRepository.findByToken(input.token)
        if (existing != null) {
            existing.seller = seller
            existing.platform = input.platform
            existing.lastSeenAt = Instant.now()
            pushTokenRepository.save(existing)
        } else {
            pushTokenRepository.save(PushToken(seller = seller, token = input.token, platform = input.platform))
        }
    }

    /** Called on sign-out — stops this device from receiving further pushes for whichever seller was signed in. */
    fun unregister(input: UnregisterPushTokenInput) {
        pushTokenRepository.deleteByToken(input.token)
    }
}
