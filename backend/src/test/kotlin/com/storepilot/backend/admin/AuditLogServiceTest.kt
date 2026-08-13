package com.storepilot.backend.admin

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.UUID

class AuditLogServiceTest {
    private val auditLogRepository = mockk<AuditLogRepository>(relaxed = true)
    private val currentActor = mockk<CurrentActor>()
    private val service = AuditLogService(auditLogRepository, currentActor)

    @Test
    fun `record resolves the actor from the current admin session`() {
        val admin = Admin(cognitoSub = "admin-sub", email = "admin@example.com", name = "Admin").apply { id = UUID.randomUUID() }
        every { currentActor.requireAdmin() } returns admin
        val slot = slot<AuditLog>()
        every { auditLogRepository.save(capture(slot)) } answers { firstArg() }

        service.record(AuditAction.STORE_APPROVED, "store", "store-1", "Approved store \"Test\"")

        assertEquals(admin.email, slot.captured.actorEmail)
        assertEquals(admin.id, slot.captured.actorId)
        assertEquals(AuditAction.STORE_APPROVED, slot.captured.action)
    }

    @Test
    fun `record throws when the caller isn't an admin — it can never be used for seller-initiated actions`() {
        every { currentActor.requireAdmin() } throws ForbiddenException("An admin account is required for this action")

        assertThrows(ForbiddenException::class.java) {
            service.record(AuditAction.STORE_APPROVED, "store", "store-1", "irrelevant")
        }
        verify(exactly = 0) { auditLogRepository.save(any()) }
    }

    @Test
    fun `recordAsSeller uses the given seller directly, without going through CurrentActor`() {
        val seller = Seller(cognitoSub = "seller-sub", email = "seller@example.com", name = "Seller").apply { id = UUID.randomUUID() }
        val slot = slot<AuditLog>()
        every { auditLogRepository.save(capture(slot)) } answers { firstArg() }

        service.recordAsSeller(seller, AuditAction.STORE_SETTINGS_UPDATED, "store", "store-1", "Updated settings")

        assertEquals(seller.email, slot.captured.actorEmail)
        assertEquals(seller.id, slot.captured.actorId)
        assertEquals(AuditAction.STORE_SETTINGS_UPDATED, slot.captured.action)
    }

    @Test
    fun `recordAdminLogin writes an entry with the given email and a null actorId`() {
        val slot = slot<AuditLog>()
        every { auditLogRepository.save(capture(slot)) } answers { firstArg() }

        service.recordAdminLogin("admin@example.com")

        assertEquals("admin@example.com", slot.captured.actorEmail)
        assertEquals(null, slot.captured.actorId)
        assertEquals(AuditAction.ADMIN_LOGIN, slot.captured.action)
    }
}
