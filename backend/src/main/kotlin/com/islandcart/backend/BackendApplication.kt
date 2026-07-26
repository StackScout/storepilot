package com.islandcart.backend

import com.islandcart.backend.common.AwsProperties
import com.islandcart.backend.notification.NotificationProperties
import com.islandcart.backend.order.PayHereProperties
import com.islandcart.backend.order.ReceiptStorageProperties
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
)
@EnableScheduling
class BackendApplication

fun main(args: Array<String>) {
	runApplication<BackendApplication>(*args)
}
