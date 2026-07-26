package com.islandcart.backend.buyer

import com.islandcart.backend.common.ConflictException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.ShippingDetails
import com.islandcart.backend.order.ShippingDetailsInput
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class BuyerService(
    private val buyerRepository: BuyerRepository,
) {
    /** GET /api/buyers/by-email — a real lookup, unlike seller /login's mock. */
    fun getByEmail(email: String): BuyerResponse? = buyerRepository.findByEmailIgnoreCase(email.trim())?.toResponse()

    fun getById(id: UUID): BuyerResponse =
        buyerRepository.findById(id).orElseThrow { NotFoundException("Buyer $id not found") }.toResponse()

    /** POST /api/buyers — register. Rejects a duplicate email, matching buyers.service.ts#registerBuyer. */
    @Transactional
    fun register(input: BuyerRegistrationInput): BuyerResponse {
        if (buyerRepository.findByEmailIgnoreCase(input.email.trim()) != null) {
            throw ConflictException("An account with this email already exists. Try signing in instead.")
        }
        val buyer = Buyer(name = input.name, email = input.email.trim(), phone = input.phone)
        return buyerRepository.save(buyer).toResponse()
    }

    /** PATCH /api/buyers/{id}/default-shipping */
    @Transactional
    fun updateDefaultShipping(id: UUID, input: ShippingDetailsInput): BuyerResponse {
        val buyer = buyerRepository.findById(id).orElseThrow { NotFoundException("Buyer $id not found") }
        buyer.defaultShipping = ShippingDetails(
            fullName = input.fullName,
            phone = input.phone,
            addressLine1 = input.addressLine1,
            city = input.city,
            district = input.district,
            postalCode = input.postalCode,
        )
        return buyerRepository.save(buyer).toResponse()
    }
}
