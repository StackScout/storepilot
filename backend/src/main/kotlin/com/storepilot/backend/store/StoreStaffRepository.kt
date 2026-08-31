package com.storepilot.backend.store

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StoreStaffMemberRepository : JpaRepository<StoreStaffMember, UUID> {
    fun findBySellerId(sellerId: UUID): StoreStaffMember?
    fun existsBySellerId(sellerId: UUID): Boolean
    fun existsByStoreIdAndSellerId(storeId: UUID, sellerId: UUID): Boolean
    fun findByStoreIdOrderByCreatedAtDesc(storeId: UUID): List<StoreStaffMember>
}

interface StoreStaffInviteRepository : JpaRepository<StoreStaffInvite, UUID> {
    fun findByTokenHash(tokenHash: String): StoreStaffInvite?
    fun findByStoreIdAndEmailIgnoreCaseAndStatus(storeId: UUID, email: String, status: StoreStaffInviteStatus): StoreStaffInvite?
    fun findByStoreIdAndStatusOrderByCreatedAtDesc(storeId: UUID, status: StoreStaffInviteStatus): List<StoreStaffInvite>
}
