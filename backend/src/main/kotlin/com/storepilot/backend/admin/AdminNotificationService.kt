package com.storepilot.backend.admin

import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PageResponse
import com.storepilot.backend.common.toPageResponse
import com.storepilot.backend.notification.EmailService
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.store.Store
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Hard cap regardless of what a caller requests via `size` — same convention as ProductService/StoreService's own MAX_PAGE_SIZE. */
private const val MAX_PAGE_SIZE = 100

/**
 * Payouts happen outside this app (a seller's bank account is where the
 * platform wires money to, manually) — a seller changing those details is
 * the one action here the admin can't otherwise observe, so it's surfaced
 * two ways: an email (in case nobody's watching the admin panel) and a
 * row here for the admin panel's activity feed (in case the email gets
 * missed/spam-filtered). Not admin-account-scoped: any admin can read or
 * dismiss any row, same as ROLE_ADMIN authorization elsewhere.
 */
@Service
@Transactional(readOnly = true)
class AdminNotificationService(
    private val adminNotificationRepository: AdminNotificationRepository,
    private val emailService: EmailService,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(AdminNotificationService::class.java)

    fun list(page: Int, size: Int): PageResponse<AdminNotificationResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, MAX_PAGE_SIZE))
        return adminNotificationRepository.findAllByOrderByCreatedAtDesc(pageable).toPageResponse { it.toResponse() }
    }

    fun summary(): AdminNotificationSummaryResponse =
        AdminNotificationSummaryResponse(unreadCount = adminNotificationRepository.countByReadFalse())

    @Transactional
    fun markRead(id: UUID): AdminNotificationResponse {
        val notification = adminNotificationRepository.findById(id)
            .orElseThrow { NotFoundException("Notification $id not found") }
        notification.read = true
        return adminNotificationRepository.save(notification).toResponse()
    }

    @Transactional
    fun markAllRead() {
        adminNotificationRepository.findAllByOrderByCreatedAtDesc()
            .filter { !it.read }
            .forEach { it.read = true }
    }

    /**
     * Called from StoreService right after a seller's bank details actually
     * change — never on every settings save (see the caller's diff check).
     * Best-effort on the email, same principle as OrderNotifier: a
     * notification failure must never fail the settings save that
     * triggered it.
     */
    @Transactional
    fun notifyBankDetailsChanged(store: Store, bankName: String, bankAccountName: String, bankAccountNumber: String) {
        val message = "${store.name} changed their payout bank details: $bankName, $bankAccountName, account ending ${bankAccountNumber.takeLast(4)}"
        adminNotificationRepository.save(
            AdminNotification(
                type = AdminNotificationType.BANK_DETAILS_CHANGED,
                message = message,
                storeId = store.id,
            ),
        )
        if (notificationProperties.adminNotificationEmail.isBlank()) return
        try {
            emailService.send(
                to = notificationProperties.adminNotificationEmail,
                subject = "Payout bank details changed — ${store.name}",
                body = message,
            )
        } catch (e: Exception) {
            log.warn("Failed to send admin bank-details-changed email for store {} — not failing the settings save", store.id, e)
        }
    }

    /** Called from StoreVerificationChangeRequestService right after a seller submits a request — same best-effort-email principle as notifyBankDetailsChanged. */
    @Transactional
    fun notifyVerificationChangeRequested(store: Store) {
        val message = "${store.name} requested a change to their verification details — review it in the admin panel"
        adminNotificationRepository.save(
            AdminNotification(
                type = AdminNotificationType.VERIFICATION_CHANGE_REQUESTED,
                message = message,
                storeId = store.id,
            ),
        )
        if (notificationProperties.adminNotificationEmail.isBlank()) return
        try {
            emailService.send(
                to = notificationProperties.adminNotificationEmail,
                subject = "Verification change requested — ${store.name}",
                body = message,
            )
        } catch (e: Exception) {
            log.warn("Failed to send admin verification-change-requested email for store {} — not failing the request", store.id, e)
        }
    }
}
