package com.storepilot.backend.returns

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReturnRequestRepository : JpaRepository<ReturnRequest, UUID> {
    /** True if any request on this order is anything but REJECTED — see ReturnRequestStatus's doc comment for the eligibility rule this backs. */
    fun existsByOrder_IdAndStatusNot(orderId: UUID, status: ReturnRequestStatus): Boolean

    fun findByOrder_IdOrderByCreatedAtDesc(orderId: UUID): List<ReturnRequest>

    fun findByOrder_Store_IdOrderByCreatedAtDesc(storeId: UUID): List<ReturnRequest>

    fun findByStatusOrderByCreatedAtDesc(status: ReturnRequestStatus): List<ReturnRequest>

    fun findAllByOrderByCreatedAtDesc(): List<ReturnRequest>
}
