package com.storepilot.backend.notification

import com.storepilot.backend.buyer.Buyer
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as SellerNotificationService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/**
 * The buyer-facing mirror of SellerNotificationService — a per-buyer
 * notification-center feed with read/unread state, backing the mobile
 * account tab's notification list. [notify] is called from OrderNotifier/
 * BookingNotifier alongside their existing buyer email send, for one-shot
 * order/booking lifecycle events — see BuyerNotification's doc comment for
 * the full list.
 */
@Service
@Transactional(readOnly = true)
class BuyerNotificationService(
    private val repository: BuyerNotificationRepository,
    private val currentActor: CurrentActor,
) {
    private val log = LoggerFactory.getLogger(BuyerNotificationService::class.java)

    fun list(page: Int, size: Int): PageResponse<BuyerNotificationResponse> {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return repository.findAllByBuyerIdOrderByCreatedAtDesc(buyerId, pageable).toPageResponse { it.toResponse() }
    }

    fun summary(): BuyerNotificationSummaryResponse {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        return BuyerNotificationSummaryResponse(unreadCount = repository.countByBuyerIdAndReadFalse(buyerId))
    }

    @Transactional
    fun markRead(id: UUID): BuyerNotificationResponse {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        val notification = repository.findById(id).orElseThrow { NotFoundException("Notification $id not found") }
        if (notification.buyer.id != buyerId) throw ForbiddenException("You don't own notification $id")
        notification.read = true
        return repository.save(notification).toResponse()
    }

    @Transactional
    fun markAllRead() {
        val buyerId = requireNotNull(currentActor.requireBuyer().id)
        repository.findAllByBuyerIdOrderByCreatedAtDesc(buyerId)
            .filter { !it.read }
            .forEach { it.read = true }
    }

    /**
     * Called from OrderNotifier/BookingNotifier, right alongside their
     * existing buyer email send. Best-effort, same principle as every
     * email/push send in this codebase — a notification-center row failing
     * to persist must never fail the order/booking operation that
     * triggered it.
     */
    @Transactional
    fun notify(buyer: Buyer, type: BuyerNotificationType, title: String, body: String, entityId: UUID?) {
        try {
            repository.save(BuyerNotification(buyer = buyer, type = type, title = title, body = body, entityId = entityId))
        } catch (e: Exception) {
            log.warn("Failed to save buyer notification for buyer {} (type={}, title=\"{}\") — not failing the triggering operation", buyer.id, type, title, e)
        }
    }
}
