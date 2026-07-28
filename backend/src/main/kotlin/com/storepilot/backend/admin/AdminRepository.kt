package com.storepilot.backend.admin

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AdminRepository : JpaRepository<Admin, UUID> {
    fun findByCognitoSub(cognitoSub: String): Admin?
}
