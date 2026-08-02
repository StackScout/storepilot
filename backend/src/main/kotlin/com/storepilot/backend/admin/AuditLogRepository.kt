package com.storepilot.backend.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findAllByOrderByCreatedAtDesc(pageable: Pageable): Page<AuditLog>
    fun findByActionOrderByCreatedAtDesc(action: AuditAction, pageable: Pageable): Page<AuditLog>
    fun findByTargetTypeOrderByCreatedAtDesc(targetType: String, pageable: Pageable): Page<AuditLog>
    fun findByActionAndTargetTypeOrderByCreatedAtDesc(action: AuditAction, targetType: String, pageable: Pageable): Page<AuditLog>
}
