package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.security.CurrentActor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BuyerPushTokenServiceTest {
    private val buyerPushTokenRepository = mockk<BuyerPushTokenRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = BuyerPushTokenService(buyerPushTokenRepository, currentActor)

    private lateinit var buyer: Buyer

    @BeforeEach
    fun setUp() {
        buyer = Buyer(email = "buyer@example.com", name = "Buyer").apply { id = UUID.randomUUID() }
        every { currentActor.requireBuyer() } returns buyer
        every { buyerPushTokenRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `register creates a new row for a token that hasn't been seen before`() {
        every { buyerPushTokenRepository.findByToken("token-1") } returns null
        val slot = slot<BuyerPushToken>()

        service.register(RegisterPushTokenInput(token = "token-1", platform = "ios"))

        verify { buyerPushTokenRepository.save(capture(slot)) }
        assertEquals("token-1", slot.captured.token)
        assertEquals("ios", slot.captured.platform)
        assertEquals(buyer, slot.captured.buyer)
    }

    @Test
    fun `register upserts an existing token to the current buyer and platform`() {
        val otherBuyer = Buyer(email = "other@example.com", name = "Other").apply { id = UUID.randomUUID() }
        val existing = BuyerPushToken(buyer = otherBuyer, token = "token-1", platform = "android").apply { id = UUID.randomUUID() }
        every { buyerPushTokenRepository.findByToken("token-1") } returns existing

        service.register(RegisterPushTokenInput(token = "token-1", platform = "ios"))

        assertEquals(buyer, existing.buyer)
        assertEquals("ios", existing.platform)
        verify { buyerPushTokenRepository.save(existing) }
    }

    @Test
    fun `unregister deletes by token`() {
        every { buyerPushTokenRepository.deleteByToken("token-1") } returns Unit

        service.unregister(UnregisterPushTokenInput(token = "token-1"))

        verify { buyerPushTokenRepository.deleteByToken("token-1") }
    }
}
