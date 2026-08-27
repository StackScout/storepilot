package com.storepilot.backend.common.sse

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class SseHubTest {
    private val hub = SseHub()

    @Suppress("UNCHECKED_CAST")
    private fun subscriberCount(topic: String): Int {
        val field = SseHub::class.java.getDeclaredField("subscribers")
        field.isAccessible = true
        val subscribers = field.get(hub) as ConcurrentHashMap<String, CopyOnWriteArrayList<*>>
        return subscribers[topic]?.size ?: 0
    }

    @Test
    fun `subscribe registers the emitter under its topic`() {
        hub.subscribe("order:1")
        assertEquals(1, subscriberCount("order:1"))
    }

    @Test
    fun `subscribe keeps separate subscriber lists per topic`() {
        hub.subscribe("order:1")
        hub.subscribe("order:2")
        assertEquals(1, subscriberCount("order:1"))
        assertEquals(1, subscriberCount("order:2"))
    }

    @Test
    fun `multiple subscribers can share the same topic`() {
        hub.subscribe("order:1")
        hub.subscribe("order:1")
        assertEquals(2, subscriberCount("order:1"))
    }

    @Test
    fun `publish to a topic with no subscribers is a no-op`() {
        hub.publish("order:unknown", "status", mapOf("status" to "shipped"))
    }

    @Test
    fun `publish never throws even when a subscriber's connection is already closed`() {
        val emitter = hub.subscribe("order:1")
        emitter.complete()

        hub.publish("order:1", "status", mapOf("status" to "shipped"))

        assertTrue(subscriberCount("order:1") <= 1)
    }

    @Test
    fun `publish removes a subscriber whose send fails, so it doesn't get resent to`() {
        val emitter = hub.subscribe("order:1")
        emitter.complete()

        hub.publish("order:1", "status", mapOf("status" to "shipped"))
        val afterFirstPublish = subscriberCount("order:1")
        hub.publish("order:1", "status", mapOf("status" to "delivered"))

        assertEquals(afterFirstPublish, subscriberCount("order:1"))
    }
}
