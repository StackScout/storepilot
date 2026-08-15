package com.storepilot.backend.buyer

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class SavedSearchService(
    private val savedSearchRepository: SavedSearchRepository,
    private val currentActor: CurrentActor,
) {
    /** Same "explicitly @Transactional, not readOnly" reasoning as AddressService.list() — requireBuyer() may JIT-provision a new row on the caller's first request. */
    @Transactional
    fun list(): List<SavedSearchResponse> {
        val buyer = currentActor.requireBuyer()
        return savedSearchRepository.findByBuyerIdOrderByCreatedAtDesc(requireNotNull(buyer.id)).map { it.toResponse() }
    }

    @Transactional
    fun create(input: SavedSearchInput): SavedSearchResponse {
        val buyer = currentActor.requireBuyer()
        val savedSearch = SavedSearch(buyer = buyer, name = input.name.trim(), queryString = input.queryString)
        return savedSearchRepository.save(savedSearch).toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        val buyer = currentActor.requireBuyer()
        val savedSearch = savedSearchRepository.findById(id).orElseThrow { NotFoundException("Saved search $id not found") }
        if (savedSearch.buyer.id != buyer.id) throw ForbiddenException("Saved search $id does not belong to you")
        savedSearchRepository.delete(savedSearch)
    }
}
