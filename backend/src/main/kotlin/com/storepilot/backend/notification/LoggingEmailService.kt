package com.storepilot.backend.notification

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * Mock EmailService — logs instead of sending. Default/dev implementation,
 * active whenever the `aws` profile isn't. See SesEmailService for the real
 * implementation used in the aws profile — no business-layer changes needed
 * to swap between them.
 */
@Service
@Profile("!aws")
class LoggingEmailService : EmailService {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    override fun send(to: String, subject: String, body: String, attachment: EmailAttachment?) {
        if (attachment != null) {
            log.info(
                "[mock email] to={} subject=\"{}\" attachment={} ({} bytes)\n{}",
                to, subject, attachment.filename, attachment.bytes.size, body,
            )
        } else {
            log.info("[mock email] to={} subject=\"{}\"\n{}", to, subject, body)
        }
    }
}
