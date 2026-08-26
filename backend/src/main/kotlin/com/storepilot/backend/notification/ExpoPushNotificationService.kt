package com.storepilot.backend.notification

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * The only PushNotificationService implementation — no local-dev mock
 * needed, since sending is a harmless no-op with no real tokens registered
 * locally (same "boots fine before real creds exist" posture as
 * AbnLookupService/StripeService; expoAccessToken is optional, see
 * NotificationProperties). Posts to Expo's push relay
 * (https://exp.host/--/api/v2/push/send), which forwards to APNs/FCM —
 * nothing here talks to Apple/Google directly. Batches at Expo's
 * documented 100-notifications-per-request limit.
 */
@Service
class ExpoPushNotificationService(
    private val notificationProperties: NotificationProperties,
) : PushNotificationService {
    private val log = LoggerFactory.getLogger(ExpoPushNotificationService::class.java)
    private val restClient = RestClient.create()

    override fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>) {
        if (tokens.isEmpty()) return

        tokens.chunked(EXPO_BATCH_LIMIT).forEach { batch ->
            sendBatch(batch, title, body, data)
        }
    }

    private fun sendBatch(tokens: List<String>, title: String, body: String, data: Map<String, String>) {
        val messages = tokens.map { ExpoPushMessage(to = it, title = title, body = body, data = data) }
        try {
            val response = restClient.post()
                .uri("https://exp.host/--/api/v2/push/send")
                .headers { headers ->
                    headers.set("Content-Type", "application/json")
                    headers.set("Accept", "application/json")
                    if (notificationProperties.expoAccessToken.isNotBlank()) {
                        headers.setBearerAuth(notificationProperties.expoAccessToken)
                    }
                }
                .body(messages)
                .retrieve()
                .body(ExpoPushApiResponse::class.java)

            response?.data.orEmpty().forEachIndexed { i, ticket ->
                if (ticket.status != "ok") {
                    log.warn(
                        "Expo push to {} failed: {} ({})",
                        tokens.getOrNull(i),
                        ticket.message,
                        ticket.details?.error,
                    )
                }
            }
        } catch (e: RestClientException) {
            log.warn("Expo push batch of {} failed", tokens.size, e)
        }
    }

    private companion object {
        const val EXPO_BATCH_LIMIT = 100
    }
}

private data class ExpoPushMessage(
    val to: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ExpoPushApiResponse(val data: List<ExpoPushTicket> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ExpoPushTicket(
    val status: String,
    val message: String? = null,
    val details: ExpoPushTicketDetails? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ExpoPushTicketDetails(val error: String? = null)
