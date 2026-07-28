package com.storepilot.backend.seller

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SellerRepository : JpaRepository<Seller, UUID> {
    fun findByCognitoSub(cognitoSub: String): Seller?
    fun findByEmailIgnoreCase(email: String): Seller?
}
