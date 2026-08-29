package com.storepilot.backend.notification

import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class PushTokenServiceTest {
    private val pushTokenRepository = mockk<PushTokenRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = PushTokenService(pushTokenRepository, currentActor)

    private lateinit var seller: Seller

    @BeforeEach
    fun setUp() {
        seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        every { currentActor.requireSeller() } returns seller
        every { pushTokenRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `register creates a new row for a token that hasn't been seen before`() {
        every { pushTokenRepository.findByToken("token-1") } returns null
        val slot = slot<PushToken>()

        service.register(RegisterPushTokenInput(token = "token-1", platform = "ios"))

        verify { pushTokenRepository.save(capture(slot)) }
        assertEquals("token-1", slot.captured.token)
        assertEquals("ios", slot.captured.platform)
        assertEquals(seller, slot.captured.seller)
    }

    @Test
    fun `register upserts an existing token to the current seller and platform`() {
        val otherSeller = Seller(cognitoSub = "other-sub", email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        val existing = PushToken(seller = otherSeller, token = "token-1", platform = "android").apply { id = UUID.randomUUID() }
        every { pushTokenRepository.findByToken("token-1") } returns existing

        service.register(RegisterPushTokenInput(token = "token-1", platform = "ios"))

        assertEquals(seller, existing.seller)
        assertEquals("ios", existing.platform)
        verify { pushTokenRepository.save(existing) }
    }

    @Test
    fun `unregister deletes by token`() {
        every { pushTokenRepository.deleteByToken("token-1") } returns Unit

        service.unregister(UnregisterPushTokenInput(token = "token-1"))

        verify { pushTokenRepository.deleteByToken("token-1") }
    }
}
