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
 * Mirrors OrderNotifier exactly — booking-lifecycle email copy plus, since
 * BookingReminderJob was added, a time-based "your appointment is coming
 * up" reminder (see bookingReminder below and docs/features/bookings.md).
 */
@Component
class BookingNotifier(
    private val emailService: EmailService,
    private val notificationProperties: NotificationProperties,
    private val platformConfigService: PlatformConfigService,
    private val pushNotificationService: PushNotificationService,
    private val pushTokenRepository: PushTokenRepository,
    private val sellerNotificationService: SellerNotificationService,
    private val buyerPushTokenRepository: BuyerPushTokenRepository,
    private val buyerNotificationService: BuyerNotificationService,
) {
    private val log = LoggerFactory.getLogger(BookingNotifier::class.java)

    /** Fired the moment a buyer books a slot — see BookingService.createBooking. */
    fun sellerBookingCreated(booking: Booking) {
        val title = "New booking: ${booking.serviceName}"
        val body = "${booking.buyerName} booked ${booking.serviceName} for ${formatScheduledTime(booking)}."
        sendPushToSeller(booking, title, body)
        sellerNotificationService.notify(booking.store.seller, SellerNotificationType.BOOKING, title, body, booking.id)
    }

    /** Fired by SellerBookingReminderJob, StoreSettings.sellerBookingReminderMinutesBefore before scheduledStart. */
    fun sellerBookingReminder(booking: Booking) {
        sendPushToSeller(
            booking,
            title = "Upcoming booking: ${booking.serviceName}",
            body = "${booking.serviceName} with ${booking.buyerName} is coming up at ${formatScheduledTime(booking)}.",
        )
    }

    private fun sendPushToSeller(booking: Booking, title: String, body: String) {
        val sellerId = booking.store.seller.id ?: return
        val tokens = pushTokenRepository.findBySellerId(sellerId).map { it.token }
        if (tokens.isEmpty()) return
        try {
            pushNotificationService.send(tokens, title, body, data = mapOf("type" to "booking", "id" to booking.id.toString()))
        } catch (e: Exception) {
            log.warn("Failed to send booking push to seller {} (title=\"{}\") — not failing the triggering operation", sellerId, title, e)
        }
    }

    /** Push + notification-center entry for the buyer — mirrors OrderNotifier.notifyBuyer, see its doc comment for the guest-checkout no-op case. */
    private fun notifyBuyer(booking: Booking, title: String, body: String) {
        val buyer = booking.buyer ?: return
        val buyerId = buyer.id ?: return
        val tokens = buyerPushTokenRepository.findByBuyerId(buyerId).map { it.token }
        if (tokens.isNotEmpty()) {
            try {
                pushNotificationService.send(tokens, title, body, data = mapOf("type" to "booking", "id" to booking.id.toString()))
            } catch (e: Exception) {
                log.warn("Failed to send booking push to buyer {} (title=\"{}\") — not failing the triggering operation", buyerId, title, e)
            }
        }
        buyerNotificationService.notify(buyer, BuyerNotificationType.BOOKING, title, body, booking.id)
    }

    /** BookingService.cancelBooking — the buyer cancels their own booking; seller-facing, mirrors OrderNotifier.orderCancelledByBuyer's push+in-app-only shape. */
    fun sellerNotifiedOfBuyerCancellation(booking: Booking) {
        val title = "Booking cancelled: ${booking.serviceName}"
        val body = "${booking.buyerName} cancelled their booking for ${formatScheduledTime(booking)}."
        sendPushToSeller(booking, title, body)
        sellerNotificationService.notify(booking.store.seller, SellerNotificationType.BOOKING, title, body, booking.id)
    }

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
        notifyBuyer(booking, title = "Booking requested", body = "Your booking for ${booking.serviceName} at ${formatScheduledTime(booking)} has been requested.")
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
        notifyBuyer(booking, title = "Booking confirmed", body = "${booking.store.name} confirmed your booking for ${formatScheduledTime(booking)}.")
    }

    /** BookingService.updateStatus — seller marks the booking COMPLETED. */
    fun bookingCompleted(booking: Booking) {
        sendSafely(
            to = booking.buyerEmail,
            subject = "Booking ${booking.bookingNumber} completed",
            body = buildString {
                appendLine("Your appointment for ${booking.serviceName} with ${booking.store.name} is complete.")
                appendLine()
                appendLine("View your booking: ${bookingUrl(booking)}")
            },
        )
        notifyBuyer(booking, title = "Booking completed", body = "Your appointment for ${booking.serviceName} with ${booking.store.name} is complete.")
    }

    /** BookingService.updateStatus — seller marks the booking NO_SHOW. */
    fun bookingNoShow(booking: Booking) {
        sendSafely(
            to = booking.buyerEmail,
            subject = "Booking ${booking.bookingNumber} marked as a no-show",
            body = buildString {
                appendLine("${booking.store.name} marked your booking for ${booking.serviceName} (${formatScheduledTime(booking)}) as a no-show.")
                appendLine()
                appendLine("Contact the store if you think this is a mistake: ${bookingUrl(booking)}")
            },
        )
        notifyBuyer(booking, title = "Marked as no-show", body = "${booking.store.name} marked your booking for ${booking.serviceName} as a no-show.")
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
        notifyBuyer(booking, title = "Booking cancelled", body = "Your booking for ${booking.serviceName} with ${booking.store.name} was cancelled.")
    }

    fun bookingReminder(booking: Booking) {
        sendSafely(
            to = booking.buyerEmail,
            subject = "Reminder: your booking ${booking.bookingNumber} is coming up",
            body = buildString {
                appendLine("This is a reminder about your upcoming booking with ${booking.store.name}.")
                appendLine()
                appendLine("Service: ${booking.serviceName}")
                appendLine("When: ${formatScheduledTime(booking)}")
                appendLine()
                appendLine("View your booking: ${bookingUrl(booking)}")
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
