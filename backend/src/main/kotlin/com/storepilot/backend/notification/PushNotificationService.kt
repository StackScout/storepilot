package com.storepilot.backend.notification

/**
 * Transport-only — no template/copy knowledge, same split as EmailService.
 * [data] is an arbitrary string-keyed payload the mobile app reads on tap
 * to deep-link (e.g. `{"type": "order", "id": "..."}`) — see
 * ExpoPushNotificationService for the one real implementation.
 */
interface PushNotificationService {
    fun send(tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap())
}
