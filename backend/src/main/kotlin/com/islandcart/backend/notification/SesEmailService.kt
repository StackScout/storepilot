package com.islandcart.backend.notification

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.Body
import software.amazon.awssdk.services.ses.model.Content
import software.amazon.awssdk.services.ses.model.Destination
import software.amazon.awssdk.services.ses.model.Message
import software.amazon.awssdk.services.ses.model.SendEmailRequest

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
    override fun send(to: String, subject: String, body: String) {
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
}
