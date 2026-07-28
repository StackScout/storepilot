package com.storepilot.backend.notification

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.RawMessage
import software.amazon.awssdk.services.ses.model.SendEmailRequest
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest
import java.util.Base64
import java.util.UUID

/**
 * Real EmailService implementation for the aws profile — see
 * LoggingEmailService for the local/dev mock. SES starts in sandbox mode
 * for a new account: it can only send to addresses you've individually
 * verified until you request production access from the SES console (see
 * infra/README.md) — that's an AWS-account step, not something this code
 * can work around.
 */
@Service
@Profile("aws")
class SesEmailService(
    private val sesClient: SesClient,
    private val notificationProperties: NotificationProperties,
) : EmailService {
    override fun send(to: String, subject: String, body: String, attachment: EmailAttachment?) {
        if (attachment == null) {
            sendSimple(to, subject, body)
        } else {
            sendRawWithAttachment(to, subject, body, attachment)
        }
    }

    private fun sendSimple(to: String, subject: String, body: String) {
        val request = SendEmailRequest.builder()
            .source(notificationProperties.sesSenderEmail)
            .destination(Destination.builder().toAddresses(to).build())
            .message(
                Message.builder()
                    .subject(Content.builder().data(subject).build())
                    .body(Body.builder().text(Content.builder().data(body).build()).build())
                    .build(),
            )
            .build()
        sesClient.sendEmail(request)
    }

    /**
     * SES's simple sendEmail API has no attachment support — an attachment
     * requires building the raw RFC 5322 MIME message ourselves (a
     * multipart/mixed body: one text/plain part, one attachment part
     * base64-encoded) and sending it via sendRawEmail instead. No mail
     * library dependency added for this — the format is simple enough to
     * hand-build for the one-attachment case this app needs.
     */
    private fun sendRawWithAttachment(to: String, subject: String, body: String, attachment: EmailAttachment) {
        val boundary = "----storepilot-${UUID.randomUUID()}"
        val encodedAttachment = Base64.getMimeEncoder(76, "\r\n".toByteArray()).encodeToString(attachment.bytes)
        val raw = buildString {
            append("From: ${notificationProperties.sesSenderEmail}\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
            append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: 7bit\r\n")
            append("\r\n")
            append(body)
            append("\r\n")
            append("--$boundary\r\n")
            append("Content-Type: ${attachment.contentType}; name=\"${attachment.filename}\"\r\n")
            append("Content-Disposition: attachment; filename=\"${attachment.filename}\"\r\n")
            append("Content-Transfer-Encoding: base64\r\n")
            append("\r\n")
            append(encodedAttachment)
            append("\r\n")
            append("--$boundary--\r\n")
        }
        val request = SendRawEmailRequest.builder()
            .rawMessage(RawMessage.builder().data(SdkBytes.fromUtf8String(raw)).build())
            .build()
        sesClient.sendRawEmail(request)
    }
}
