package com.storepilot.backend.admin

import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val MAX_PAGE_SIZE = 100

/**
 * Write side is called from within other services' own @Transactional
 * methods (StoreService.setVerificationStatus, PayoutService.markPaid,
 * ...) — record() joins that same transaction (default REQUIRED
 * propagation) rather than opening its own, so a rolled-back caller never
 * leaves an orphaned audit row behind.
 */
@Service
@Transactional(readOnly = true)
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
    private val currentActor: CurrentActor,
) {
    @Transactional
    fun record(action: AuditAction, targetType: String?, targetId: String?, description: String) {
        val admin = currentActor.requireAdmin()
        auditLogRepository.save(
            AuditLog(
                actorEmail = admin.email,
                actorId = admin.id,
                action = action,
                targetType = targetType,
                targetId = targetId,
                description = description,
            ),
        )
    }

    fun list(action: AuditAction?, targetType: String?, page: Int, size: Int): PageResponse<AuditLogResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        val result = when {
            action != null && targetType != null -> auditLogRepository.findByActionAndTargetTypeOrderByCreatedAtDesc(action, targetType, pageable)
            action != null -> auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable)
            targetType != null -> auditLogRepository.findByTargetTypeOrderByCreatedAtDesc(targetType, pageable)
            else -> auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
        }
        return result.toPageResponse { it.toResponse() }
    }
}
