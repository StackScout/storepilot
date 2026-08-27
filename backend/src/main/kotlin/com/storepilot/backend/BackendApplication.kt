package com.storepilot.backend

import com.storepilot.backend.abn.AbrProperties
import com.storepilot.backend.common.AwsProperties
import com.storepilot.backend.common.PlatformProperties
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.EndpointPermissionsProperties
import com.storepilot.backend.common.storage.FileStorageProperties
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.order.ReceiptStorageProperties
import com.storepilot.backend.payhere.PayHereProperties
import com.storepilot.backend.stripe.StripeProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableConfigurationProperties(
    PayHereProperties::class,
    ReceiptStorageProperties::class,
    NotificationProperties::class,
    AwsProperties::class,
    CognitoProperties::class,
    FileStorageProperties::class,
    PlatformProperties::class,
    StripeProperties::class,
    AbrProperties::class,
    EndpointPermissionsProperties::class,
)
@EnableScheduling
class BackendApplication

fun main(args: Array<String>) {
	runApplication<BackendApplication>(*args)
}
