package com.storepilot.backend.buyer

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A buyer's saved address book — entirely separate from `Order.shipping`,
 * which snapshots its own copy of the same fields at checkout time and
 * never references an Address row (see Address.kt's doc comment).
 */
@Service
@Transactional(readOnly = true)
class AddressService(
    private val addressRepository: AddressRepository,
    private val currentActor: CurrentActor,
) {
    /** Same "explicitly @Transactional, not readOnly" reasoning as BuyerService.getCurrent() — requireBuyer() may JIT-provision a new row on the caller's first request. */
    @Transactional
    fun list(): List<AddressResponse> {
        val buyer = currentActor.requireBuyer()
        return addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(requireNotNull(buyer.id))
            .map { it.toResponse() }
    }

    /** A buyer's very first saved address always becomes their default, regardless of [AddressInput.isDefault] — there's never a valid reason for someone's only address not to be the default. */
    @Transactional
    fun create(input: AddressInput): AddressResponse {
        val buyer = currentActor.requireBuyer()
        val existing = addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(requireNotNull(buyer.id))
        val makeDefault = input.isDefault || existing.isEmpty()
        if (makeDefault) unsetCurrentDefault(existing)
        val address = Address(
            buyer = buyer,
            label = input.label?.trim()?.takeIf { it.isNotBlank() },
            shipping = input.shipping.toShippingDetails(),
            isDefault = makeDefault,
        )
        return addressRepository.save(address).toResponse()
    }

    @Transactional
    fun update(id: UUID, input: AddressInput): AddressResponse {
        val address = requireOwnedAddress(id)
        if (input.isDefault && !address.isDefault) {
            unsetCurrentDefault(addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(requireNotNull(address.buyer.id)))
            address.isDefault = true
        }
        address.label = input.label?.trim()?.takeIf { it.isNotBlank() }
        address.shipping = input.shipping.toShippingDetails()
        return addressRepository.save(address).toResponse()
    }

    @Transactional
    fun setDefault(id: UUID): AddressResponse {
        val address = requireOwnedAddress(id)
        unsetCurrentDefault(addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(requireNotNull(address.buyer.id)))
        address.isDefault = true
        return addressRepository.save(address).toResponse()
    }

    /** If the deleted address was the default, promotes the next-oldest remaining one — a buyer with any saved addresses left always has exactly one default. */
    @Transactional
    fun delete(id: UUID) {
        val address = requireOwnedAddress(id)
        val buyerId = requireNotNull(address.buyer.id)
        val wasDefault = address.isDefault
        addressRepository.delete(address)
        if (wasDefault) {
            addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyerId).firstOrNull()?.let {
                it.isDefault = true
                addressRepository.save(it)
            }
        }
    }

    private fun requireOwnedAddress(id: UUID): Address {
        val buyer = currentActor.requireBuyer()
        val address = addressRepository.findById(id).orElseThrow { NotFoundException("Address $id not found") }
        if (address.buyer.id != buyer.id) throw ForbiddenException("Address $id does not belong to you")
        return address
    }

    private fun unsetCurrentDefault(addresses: List<Address>) {
        addresses.filter { it.isDefault }.forEach {
            it.isDefault = false
            addressRepository.save(it)
        }
    }
}
