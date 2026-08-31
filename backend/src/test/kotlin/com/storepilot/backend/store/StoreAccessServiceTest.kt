package com.storepilot.backend.store

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class StoreAccessServiceTest {
    private val currentActor = mockk<CurrentActor>()
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>()

    private val service = StoreAccessService(currentActor, storeStaffMemberRepository)

    private val owner = Seller(cognitoSub = "owner-sub", email = "owner@example.com", name = "Owner").apply { id = UUID.randomUUID() }
    private val staff = Seller(cognitoSub = "staff-sub", email = "staff@example.com", name = "Staff").apply { id = UUID.randomUUID() }
    private val stranger = Seller(cognitoSub = "stranger-sub", email = "stranger@example.com", name = "Stranger").apply { id = UUID.randomUUID() }
    private lateinit var store: Store

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = owner,
            slug = "store-1",
            name = "Store",
            tagline = "tagline",
            description = "description",
            category = "fashion",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
        ).apply { id = UUID.randomUUID() }
    }

    @Test
    fun `isOperationalAccess is true for the owner without consulting the staff table`() {
        assertTrue(service.isOperationalAccess(store, owner))
    }

    @Test
    fun `isOperationalAccess is true for a linked staff member`() {
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(store.id!!, staff.id!!) } returns true
        assertTrue(service.isOperationalAccess(store, staff))
    }

    @Test
    fun `isOperationalAccess is false for a seller with no link to the store`() {
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(store.id!!, stranger.id!!) } returns false
        assertFalse(service.isOperationalAccess(store, stranger))
    }

    @Test
    fun `requireOperationalAccess returns the store for the owner`() {
        every { currentActor.requireSeller() } returns owner
        assertEquals(store, service.requireOperationalAccess(store))
    }

    @Test
    fun `requireOperationalAccess returns the store for linked staff`() {
        every { currentActor.requireSeller() } returns staff
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(store.id!!, staff.id!!) } returns true
        assertEquals(store, service.requireOperationalAccess(store))
    }

    @Test
    fun `requireOperationalAccess rejects an unrelated seller`() {
        every { currentActor.requireSeller() } returns stranger
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(store.id!!, stranger.id!!) } returns false
        assertThrows(ForbiddenException::class.java) { service.requireOperationalAccess(store) }
    }

    @Test
    fun `requireOwnerAccess accepts the owner`() {
        every { currentActor.requireSeller() } returns owner
        assertEquals(store, service.requireOwnerAccess(store))
    }

    @Test
    fun `requireOwnerAccess rejects staff even though they have operational access`() {
        every { currentActor.requireSeller() } returns staff
        assertThrows(ForbiddenException::class.java) { service.requireOwnerAccess(store) }
    }

    @Test
    fun `requireOwnerSeller accepts a seller who owns their own store`() {
        every { currentActor.requireSeller() } returns owner
        every { storeStaffMemberRepository.existsBySellerId(owner.id!!) } returns false
        assertEquals(owner, service.requireOwnerSeller())
    }

    @Test
    fun `requireOwnerSeller rejects a staff seller`() {
        every { currentActor.requireSeller() } returns staff
        every { storeStaffMemberRepository.existsBySellerId(staff.id!!) } returns true
        assertThrows(ForbiddenException::class.java) { service.requireOwnerSeller() }
    }
}
