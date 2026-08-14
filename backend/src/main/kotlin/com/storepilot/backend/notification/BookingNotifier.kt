package com.storepilot.backend.notification

import com.storepilot.backend.booking.Booking
import com.storepilot.backend.common.PlatformConfigService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Mirrors OrderNotifier exactly — booking-lifecycle email copy, status-
 * change touchpoints only (created/confirmed/cancelled), no time-based
 * "24h before your appointment" reminder job — see docs/features/bookings.md's
 * explicit v1 scope note.
 */
@Component
class BookingNotifier(
    private val emailService: EmailService,
    private val notificationProperties: NotificationProperties,
    private val platformConfigService: PlatformConfigService,
) {
    private val log = LoggerFactory.getLogger(BookingNotifier::class.java)

    fun bookingCreated(booking: Booking) {
        val platformConfig = platformConfigService.current()
        sendSafely(
            to = booking.buyerEmail,
            subject = "Your ${platformConfig.name} booking ${booking.bookingNumber} is requested",
            body = buildString {
                appendLine("Thanks for booking with ${booking.store.name}!")
                appendLine()
                appendLine("Booking: ${booking.bookingNumber}")
                appendLine("Service: ${booking.serviceName}")
                appendLine("When: ${formatScheduledTime(booking)}")
                appendLine("Total: ${platformConfig.currencyCode} ${formatMoney(booking.total)}")
                appendLine()
                appendLine("View your booking: ${bookingUrl(booking)}")
            },
        )
    }

    fun bookingConfirmed(booking: Booking) {
        sendSafely(
            to = booking.buyerEmail,
            subject = "Booking ${booking.bookingNumber} confirmed",
            body = buildString {
                appendLine("${booking.store.name} has confirmed your booking.")
                appendLine()
                appendLine("Service: ${booking.serviceName}")
                appendLine("When: ${formatScheduledTime(booking)}")
                appendLine()
                appendLine("View your booking: ${bookingUrl(booking)}")
            },
        )
    }

    fun bookingCancelled(booking: Booking) {
        sendSafely(
            to = booking.buyerEmail,
            subject = "Booking ${booking.bookingNumber} cancelled",
            body = buildString {
                appendLine("Your booking with ${booking.store.name} has been cancelled.")
                appendLine()
                appendLine("Service: ${booking.serviceName}")
                appendLine("Was scheduled for: ${formatScheduledTime(booking)}")
                if (!booking.cancellationReason.isNullOrBlank()) {
                    appendLine("Reason: ${booking.cancellationReason}")
                }
                appendLine()
                appendLine(bookingUrl(booking))
            },
        )
    }

    /** Mirrors OrderNotifier.sendSafely — never fails the triggering operation. */
    private fun sendSafely(to: String, subject: String, body: String) {
        try {
            emailService.send(to, subject, body)
        } catch (e: Exception) {
            log.warn("Failed to send notification email to {} (subject=\"{}\") — not failing the triggering operation", to, subject, e)
        }
    }

    private fun bookingUrl(booking: Booking): String = "${notificationProperties.frontendBaseUrl}/bookings/${booking.id}"

    private fun formatScheduledTime(booking: Booking): String {
        val zone = ZoneId.of(platformConfigService.current().timezone)
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(zone).format(booking.scheduledStart)
    }

    /** [cents] is this codebase's storage unit (see Product.price's doc comment) — plain-text email copy wants a decimal-string dollar amount. */
    private fun formatMoney(cents: Int): String = BigDecimal(cents).movePointLeft(2).toPlainString()
}
