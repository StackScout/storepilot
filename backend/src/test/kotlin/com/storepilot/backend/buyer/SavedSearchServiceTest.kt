package com.storepilot.backend.buyer

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import java.util.UUID

class SavedSearchServiceTest {
    private val savedSearchRepository = mockk<SavedSearchRepository>()
    private val currentActor = mockk<CurrentActor>()

    private val service = SavedSearchService(savedSearchRepository, currentActor)

    private val buyer = Buyer(name = "Jane", email = "buyer@example.com").apply { id = UUID.randomUUID() }

    @BeforeEach
    fun setUp() {
        every { currentActor.requireBuyer() } returns buyer
    }

    @Test
    fun `create trims the name and saves it against the current buyer`() {
        every { savedSearchRepository.save(any()) } answers {
            (firstArg() as SavedSearch).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        }

        val result = service.create(SavedSearchInput(name = "  Necklaces  ", queryString = "q=necklace"))

        assertEquals("Necklaces", result.name)
        assertEquals("q=necklace", result.queryString)
    }

    @Test
    fun `delete rejects a saved search belonging to another buyer`() {
        val otherBuyer = Buyer(name = "Other", email = "other@example.com").apply { id = UUID.randomUUID() }
        val savedSearch = SavedSearch(buyer = otherBuyer, name = "Necklaces", queryString = "q=necklace").apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { savedSearchRepository.findById(savedSearch.id!!) } returns Optional.of(savedSearch)

        assertThrows(ForbiddenException::class.java) { service.delete(savedSearch.id!!) }
    }

    @Test
    fun `delete throws for a missing saved search`() {
        val id = UUID.randomUUID()
        every { savedSearchRepository.findById(id) } returns Optional.empty()
        assertThrows(NotFoundException::class.java) { service.delete(id) }
    }

    @Test
    fun `delete removes the owner's own saved search`() {
        val savedSearch = SavedSearch(buyer = buyer, name = "Necklaces", queryString = "q=necklace").apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { savedSearchRepository.findById(savedSearch.id!!) } returns Optional.of(savedSearch)
        every { savedSearchRepository.delete(savedSearch) } returns Unit

        service.delete(savedSearch.id!!)

        verify { savedSearchRepository.delete(savedSearch) }
    }
}
