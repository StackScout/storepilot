package com.islandcart.backend.buyer

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BuyerRepository : JpaRepository<Buyer, UUID> {
    fun findByEmailIgnoreCase(email: String): Buyer?
    fun findByCognitoSub(cognitoSub: String): Buyer?
}
