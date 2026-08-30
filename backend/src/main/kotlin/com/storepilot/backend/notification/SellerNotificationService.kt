package com.storepilot.backend.notification

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.seller.Seller
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as every other paginated list in this codebase. */
private const val MAX_PAGE_SIZE = 100

/**
 * The seller-facing mirror of AdminNotificationService — a per-seller
 * notification-center feed with read/unread state, backing the mobile
 * dashboard's bell icon. [notify] is called from OrderNotifier/
 * BookingNotifier/ProductNotifier/MessagingNotifier/PayoutNotifier
 * alongside their existing push-notification call, for these one-shot
 * events specifically: a new order, a receipt upload, a return request, a
 * new booking, a low-stock alert, a new message, and a payout being paid
 * out. Deliberately NOT called from the scheduled reminder jobs
 * (fulfillment-due-soon/overdue, delivery-due, booking reminders, receipt
 * reminders) — those can re-fire multiple times for the same order/
 * booking, and without a dedup key that would mean duplicate rows piling
 * up in the feed for a seller who just hasn't acted yet. Revisit if
 * reminders turning into feed entries (deduplicated, e.g. one row that
 * gets its timestamp bumped rather than a new one per re-fire) is wanted
 * later.
 */
@Service
@Transactional(readOnly = true)
class SellerNotificationService(
    private val repository: SellerNotificationRepository,
    private val currentActor: CurrentActor,
) {
    private val log = LoggerFactory.getLogger(SellerNotificationService::class.java)

    fun list(page: Int, size: Int): PageResponse<SellerNotificationResponse> {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return repository.findAllBySellerIdOrderByCreatedAtDesc(sellerId, pageable).toPageResponse { it.toResponse() }
    }

    fun summary(): SellerNotificationSummaryResponse {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        return SellerNotificationSummaryResponse(unreadCount = repository.countBySellerIdAndReadFalse(sellerId))
    }

    @Transactional
    fun markRead(id: UUID): SellerNotificationResponse {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        val notification = repository.findById(id).orElseThrow { NotFoundException("Notification $id not found") }
        if (notification.seller.id != sellerId) throw ForbiddenException("You don't own notification $id")
        notification.read = true
        return repository.save(notification).toResponse()
    }

    @Transactional
    fun markAllRead() {
        val sellerId = requireNotNull(currentActor.requireSeller().id)
        repository.findAllBySellerIdOrderByCreatedAtDesc(sellerId)
            .filter { !it.read }
            .forEach { it.read = true }
    }

    /**
     * Called from the Notifier classes listed in this class's doc comment,
     * right alongside their existing push-notification send. Best-effort,
     * same principle as every email/push send in this codebase — a
     * notification-center row failing to persist must never fail the
     * order/booking/product/message/payout operation that triggered it.
     */
    @Transactional
    fun notify(seller: Seller, type: SellerNotificationType, title: String, body: String, entityId: UUID?) {
        try {
            repository.save(SellerNotification(seller = seller, type = type, title = title, body = body, entityId = entityId))
        } catch (e: Exception) {
            log.warn("Failed to save seller notification for seller {} (type={}, title=\"{}\") — not failing the triggering operation", seller.id, type, title, e)
        }
    }
}
