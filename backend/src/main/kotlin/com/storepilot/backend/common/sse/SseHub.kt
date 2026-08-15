package com.storepilot.backend.common.sse

import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

private const val EMITTER_TIMEOUT_MS = 15 * 60 * 1000L

/**
 * In-memory pub/sub for server-sent status-update events, keyed by an
 * arbitrary topic string (e.g. "order:{id}", "booking:{id}"). Single-JVM
 * only — same assumption the @Scheduled reminder jobs already make about
 * this app's current single-instance deployment; a multi-instance
 * deployment would need a shared broker (e.g. Redis pub/sub) instead.
 */
@Component
class SseHub {
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun subscribe(topic: String): SseEmitter {
        val emitter = SseEmitter(EMITTER_TIMEOUT_MS)
        val topicSubscribers = subscribers.computeIfAbsent(topic) { CopyOnWriteArrayList() }
        topicSubscribers.add(emitter)
        val remove = { topicSubscribers.remove(emitter); Unit }
        emitter.onCompletion(remove)
        emitter.onTimeout(remove)
        emitter.onError { remove() }
        // Forces the response to commit/flush immediately rather than leaving the client (and any
        // reverse proxy in front of it) waiting on a fully silent connection until the first real event.
        try {
            emitter.send(SseEmitter.event().comment("connected"))
        } catch (e: Exception) {
            remove()
        }
        return emitter
    }

    /** Best-effort — a broken/closed connection here must never fail the write that triggered it, same principle as EmailService sends. */
    fun publish(topic: String, eventName: String, data: Any) {
        val topicSubscribers = subscribers[topic] ?: return
        topicSubscribers.forEach { emitter ->
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data))
            } catch (e: Exception) {
                emitter.complete()
                topicSubscribers.remove(emitter)
            }
        }
    }
}
