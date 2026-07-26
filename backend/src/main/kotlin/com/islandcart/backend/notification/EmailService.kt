package com.islandcart.backend.notification

/**
 * Transport-only — no template/copy knowledge, just "send this text
 * somewhere". A real provider (SES, Resend, etc.) is a drop-in `@Service`
 * implementing this same interface; nothing above it (OrderNotifier, its
 * callers) needs to change. See LoggingEmailService for the current mock.
 */
interface EmailService {
    fun send(to: String, subject: String, body: String)
}
