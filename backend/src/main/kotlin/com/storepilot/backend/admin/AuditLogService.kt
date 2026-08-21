package com.storepilot.backend.admin

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.seller.Seller
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val MAX_PAGE_SIZE = 100

/**
 * Write side is called from within other services' own @Transactional
 * methods (StoreService.setVerificationStatus, PayoutService.markPaid,
 * ...) — record()/recordAsSeller() join that same transaction (default
 * REQUIRED propagation) rather than opening their own, so a rolled-back
 * caller never leaves an orphaned audit row behind.
 */
@Service
@Transactional(readOnly = true)
class AuditLogService(
    private val auditLogRepository: AuditLogRepository,
    private val currentActor: CurrentActor,
) {
    /** For admin-initiated actions — resolves the actor from the current request's own admin session. */
    @Transactional
    fun record(action: AuditAction, targetType: String?, targetId: String?, description: String) {
        val admin = currentActor.requireAdmin()
        save(actorEmail = admin.email, actorId = admin.id, action, targetType, targetId, description)
    }

    /** For seller-initiated actions (e.g. their own store settings changes) — the caller already resolved [seller], no re-lookup here. */
    @Transactional
    fun recordAsSeller(seller: Seller, action: AuditAction, targetType: String?, targetId: String?, description: String) {
        save(actorEmail = seller.email, actorId = seller.id, action, targetType, targetId, description)
    }

    /** For buyer-initiated actions (currently just account deletion) — mirrors recordAsSeller exactly. */
    @Transactional
    fun recordAsBuyer(buyer: Buyer, action: AuditAction, targetType: String?, targetId: String?, description: String) {
        save(actorEmail = buyer.email, actorId = buyer.id, action, targetType, targetId, description)
    }

    private fun save(actorEmail: String, actorId: UUID?, action: AuditAction, targetType: String?, targetId: String?, description: String) {
        auditLogRepository.save(
            AuditLog(
                actorEmail = actorEmail,
                actorId = actorId,
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
