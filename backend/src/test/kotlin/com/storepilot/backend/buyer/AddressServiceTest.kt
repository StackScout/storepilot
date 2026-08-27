package com.storepilot.backend.buyer

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.ShippingDetails
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.ShippingDetailsInput
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class AddressServiceTest {
    private val addressRepository = mockk<AddressRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = AddressService(addressRepository, currentActor)

    private val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        every { currentActor.requireBuyer() } returns buyer
        every { addressRepository.save(any()) } answers {
            (firstArg() as Address).apply {
                if (id == null) id = UUID.randomUUID()
                if (createdAt == null) createdAt = Instant.now()
            }
        }
    }

    private fun addressInput(isDefault: Boolean = false, label: String? = "Home") = AddressInput(
        label = label,
        shipping = ShippingDetailsInput(fullName = "Jane", phone = "0400000000", addressLine1 = "1 Main St", city = "Sydney", state = "NSW", postalCode = "2000"),
        isDefault = isDefault,
    )

    private fun address(isDefault: Boolean = false) = Address(
        buyer = buyer,
        label = "Home",
        shipping = ShippingDetails(fullName = "Jane", phone = "0400000000"),
        isDefault = isDefault,
    ).apply { id = UUID.randomUUID(); createdAt = Instant.now() }

    // ---- create ----

    @Test
    fun `create makes the very first address the default regardless of the input flag`() {
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns emptyList()

        val result = service.create(addressInput(isDefault = false))

        assertTrue(result.isDefault)
    }

    @Test
    fun `create unsets the previous default when the new address is marked default`() {
        val existingDefault = address(isDefault = true)
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(existingDefault)

        val result = service.create(addressInput(isDefault = true))

        assertFalse(existingDefault.isDefault)
        assertTrue(result.isDefault)
    }

    @Test
    fun `create leaves the existing default alone when the new address isn't marked default`() {
        val existingDefault = address(isDefault = true)
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(existingDefault)

        val result = service.create(addressInput(isDefault = false))

        assertTrue(existingDefault.isDefault)
        assertFalse(result.isDefault)
    }

    @Test
    fun `create trims a blank label down to null`() {
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns emptyList()

        val result = service.create(addressInput(label = "   "))

        assertEquals(null, result.label)
    }

    // ---- update ----

    @Test
    fun `update rejects an address belonging to another buyer`() {
        val otherBuyer = Buyer(name = "Other", email = "other@example.com").apply { id = UUID.randomUUID() }
        val addr = Address(buyer = otherBuyer, shipping = ShippingDetails(fullName = "Other", phone = "0499999999")).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { addressRepository.findById(addr.id!!) } returns Optional.of(addr)

        assertThrows(ForbiddenException::class.java) { service.update(addr.id!!, addressInput()) }
    }

    @Test
    fun `update throws for a missing address`() {
        val id = UUID.randomUUID()
        every { addressRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.update(id, addressInput()) }
    }

    @Test
    fun `update promotes an address to default and demotes the previous one`() {
        val addr = address(isDefault = false)
        val previousDefault = address(isDefault = true)
        every { addressRepository.findById(addr.id!!) } returns Optional.of(addr)
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(previousDefault, addr)

        val result = service.update(addr.id!!, addressInput(isDefault = true))

        assertTrue(result.isDefault)
        assertFalse(previousDefault.isDefault)
    }

    // ---- setDefault ----

    @Test
    fun `setDefault demotes every other address and promotes this one`() {
        val addr = address(isDefault = false)
        val previousDefault = address(isDefault = true)
        every { addressRepository.findById(addr.id!!) } returns Optional.of(addr)
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(previousDefault, addr)

        val result = service.setDefault(addr.id!!)

        assertTrue(result.isDefault)
        assertFalse(previousDefault.isDefault)
    }

    // ---- delete ----

    @Test
    fun `delete promotes the next-oldest remaining address when the default is removed`() {
        val addr = address(isDefault = true)
        val nextOldest = address(isDefault = false)
        every { addressRepository.findById(addr.id!!) } returns Optional.of(addr)
        every { addressRepository.delete(addr) } returns Unit
        every { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyer.id!!) } returns listOf(nextOldest)

        service.delete(addr.id!!)

        assertTrue(nextOldest.isDefault)
        verify { addressRepository.save(nextOldest) }
    }

    @Test
    fun `delete doesn't touch remaining addresses when the deleted one wasn't the default`() {
        val addr = address(isDefault = false)
        every { addressRepository.findById(addr.id!!) } returns Optional.of(addr)
        every { addressRepository.delete(addr) } returns Unit

        service.delete(addr.id!!)

        verify(exactly = 0) { addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(any()) }
    }
}
