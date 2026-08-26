package com.storepilot.backend.notification

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from NOTIFICATIONS_FRONTEND_BASE_URL / NOTIFICATIONS_FIRST_REMINDER_AFTER_HOURS /
 * NOTIFICATIONS_REMINDER_INTERVAL_HOURS env vars (application.yml).
 */
@ConfigurationProperties(prefix = "notifications")
data class NotificationProperties(
    /** Used to build the order page link in emails, as `{frontendBaseUrl}/orders/{orderId}`. */
    val frontendBaseUrl: String = "http://localhost:3000",
    /** How long a bank-transfer order can sit with no receipt before its first reminder email. */
    val firstReminderAfterHours: Long = 6,
    /** Minimum gap between reminder emails for the same order once reminders have started. */
    val reminderIntervalHours: Long = 24,
    /** "From" address for real sends — only read by SesEmailService (aws profile); must be SES-verified. */
    val sesSenderEmail: String = "",
    /** Where admin-facing alerts (e.g. a seller changing payout bank details) are emailed — must be SES-verified too, same sandbox constraint as sesSenderEmail. */
    val adminNotificationEmail: String = "",
    /** A booking gets one reminder email this many hours before its scheduledStart — see BookingReminderJob. */
    val bookingReminderBeforeHours: Long = 24,
    /** A product gets one low-stock alert email once trackStock stock drops to/below this quantity (but above zero — out-of-stock already has its own visible status) — see LowStockAlertJob. */
    val lowStockThreshold: Int = 5,
    /** Optional — restricts push sends to this Expo project (see ExpoPushNotificationService). Blank is fine: Expo's push API accepts unauthenticated requests too, this just stops anyone else sending through your project. Sourced from SSM Parameter Store in the aws profile, same as sesSenderEmail. */
    val expoAccessToken: String = "",
    /** How long before an order's fulfillment deadline (createdAt + Order.fulfillmentTimeHours) the seller gets a "ship soon" push — see OrderFulfillmentReminderJob. Global, unlike the per-store booking reminder offset, since there's no existing per-store config surface for this yet. */
    val fulfillmentDueSoonLeadHours: Long = 6,
)
