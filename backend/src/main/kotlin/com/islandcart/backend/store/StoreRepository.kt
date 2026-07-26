package com.islandcart.backend.store

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface StoreRepository : JpaRepository<Store, UUID>, JpaSpecificationExecutor<Store> {
    fun findBySlug(slug: String): Store?

    fun findByVerificationStatus(status: StoreVerificationStatus): List<Store>
}

interface StoreSettingsRepository : JpaRepository<StoreSettings, UUID>
