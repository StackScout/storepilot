package com.islandcart.backend.common

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StateRepository : JpaRepository<State, UUID> {
    fun findAllByOrderBySortOrderAscNameAsc(): List<State>
}
