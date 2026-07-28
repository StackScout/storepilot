package com.islandcart.backend.notification

import com.islandcart.backend.common.PlatformConfigService
import com.islandcart.backend.order.Order
import com.islandcart.backend.store.StoreSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal

/**
 * Owns order-lifecycle email copy and picks the right recipient for each
 * event; delegates the actual send to EmailService. Keeping "what to send"
 * here (rather than inline in OrderService) means OrderService/the reminder
 * job never touch email content directly, and a future template engine or
 * provider swap only touches this file plus EmailService's implementation.
 */
@Component
class OrderNotifier(
    private val emailService: EmailService,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val notificationProperties: NotificationProperties,
    private val platformConfigService: PlatformConfigService,
) {
    private val log = LoggerFactory.getLogger(OrderNotifier::class.java)

    fun orderConfirmed(order: Order) {
        val platformConfig = platformConfigService.current()
        sendSafely(
            to = order.buyerEmail,
            subject = "Your ${platformConfig.name} order ${order.orderNumber} is confirmed",
            body = buildString {
                appendLine("Thanks for your order from ${order.store.name}!")
                appendLine()
                appendLine("Order: ${order.orderNumber}")
                appendLine("Total: ${platformConfig.currencyCode} ${formatMoney(order.total)}")
                appendLine()
                appendLine("Track your order: ${orderUrl(order)}")
            },
        )
    }

    fun receiptUploaded(order: Order) {
        val storeId = order.store.id ?: return
        val settings = storeSettingsRepository.findById(storeId).orElse(null)
        if (settings == null) {
            log.warn("No StoreSettings for store {} — skipping receipt-uploaded notification for order {}", storeId, order.orderNumber)
            return
        }
        sendSafely(
            to = settings.contactEmail,
            subject = "Receipt uploaded for order ${order.orderNumber}",
            body = buildString {
                appendLine("A buyer has uploaded a payment receipt for order ${order.orderNumber}.")
                appendLine("Amount: ${platformConfigService.current().currencyCode} ${formatMoney(order.total)}")
                appendLine()
                appendLine("Review and verify it from your seller dashboard.")
            },
        )
    }

    fun bankTransferVerified(order: Order, approved: Boolean, note: String?) {
        val subject = if (approved) {
            "Payment confirmed for order ${order.orderNumber}"
        } else {
            "Payment receipt rejected for order ${order.orderNumber}"
        }
        val body = buildString {
            if (approved) {
                appendLine("Good news — the seller has confirmed your payment for order ${order.orderNumber}.")
            } else {
                appendLine("The seller couldn't verify your payment receipt for order ${order.orderNumber}.")
                if (!note.isNullOrBlank()) {
                    appendLine("Reason: $note")
                }
                appendLine("You can upload a new receipt or cancel the order here:")
            }
            appendLine()
            appendLine(orderUrl(order))
        }
        sendSafely(to = order.buyerEmail, subject = subject, body = body)
    }

    /** [courierReceiptFile] (if any) is attached directly from the just-uploaded file — cheaper and simpler than reading it back from storage. */
    fun orderShipped(order: Order, courierReceiptFile: MultipartFile?) {
        val attachment = courierReceiptFile?.takeIf { !it.isEmpty }?.let {
            EmailAttachment(
                filename = it.originalFilename?.takeIf { name -> name.isNotBlank() } ?: "courier-receipt",
                contentType = it.contentType ?: "application/octet-stream",
                bytes = it.bytes,
            )
        }
        sendSafely(
            to = order.buyerEmail,
            subject = "Your ${platformConfigService.current().name} order ${order.orderNumber} has shipped",
            body = buildString {
                appendLine("Good news — ${order.store.name} has handed your order ${order.orderNumber} to the courier.")
                appendLine()
                appendLine("Courier: ${order.courierServiceName}")
                appendLine("Tracking number: ${order.trackingNumber}")
                appendLine()
                appendLine("Track your order: ${orderUrl(order)}")
            },
            attachment = attachment,
        )
    }

    fun receiptReminder(order: Order) {
        sendSafely(
            to = order.buyerEmail,
            subject = "Action needed: payment receipt for order ${order.orderNumber}",
            body = buildString {
                appendLine("Your order ${order.orderNumber} from ${order.store.name} is still waiting on a payment receipt.")
                appendLine()
                appendLine("Upload your receipt or cancel the order here:")
                appendLine(orderUrl(order))
            },
        )
    }

    /**
     * Every send goes through here. A notification failure (SES throttled,
     * sandbox-mode rejection, transient network error, ...) must never fail
     * the order operation that triggered it — these calls run inside the
     * same @Transactional method that just wrote the order, and an
     * uncaught exception here would roll that back too. Best-effort: log
     * and move on, same principle as the buyer-default-shipping save in
     * OrderService#createOrder.
     *
     * TODO (deferred, not launch-blocking): move this off the request path
     * onto SQS — publish here instead of calling EmailService directly, add
     * a poller (SqsNotificationPublisher/consumer, aws profile only) that
     * calls EmailService and gets retry + a DLQ for free instead of today's
     * log-and-drop. SQS itself is free at this scale (1M requests/month,
     * permanently, long-polling keeps an idle consumer well under that) —
     * deferred because the current failure mode is low-stakes (the order
     * is already committed regardless of whether the email sends), and this
     * can't be verified locally (no LocalStack), so it's cheaper to build
     * once real traffic/launch makes it worth the AWS-deploy iteration
     * loop. Revisit when: onboarding real sellers/going live, or SES
     * latency/failures actually show up in logs.
     */
    private fun sendSafely(to: String, subject: String, body: String, attachment: EmailAttachment? = null) {
        try {
            emailService.send(to, subject, body, attachment)
        } catch (e: Exception) {
            log.warn("Failed to send notification email to {} (subject=\"{}\") — not failing the triggering operation", to, subject, e)
        }
    }

    private fun orderUrl(order: Order): String = "${notificationProperties.frontendBaseUrl}/orders/${order.id}"

    /** [cents] is this codebase's storage unit (see Product.price's doc comment) — plain-text email copy wants a decimal-string dollar amount. */
    private fun formatMoney(cents: Int): String = BigDecimal(cents).movePointLeft(2).toPlainString()
}
