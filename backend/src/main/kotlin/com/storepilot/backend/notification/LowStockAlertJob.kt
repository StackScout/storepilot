package com.storepilot.backend.notification

import com.storepilot.backend.product.ProductRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Periodically emails a store's contact email once a tracked-stock, active
 * product's stockQuantity drops to/below notifications.low-stock-threshold.
 * One-shot per drop (Product.lastLowStockAlertSentAt), cleared again on
 * restock — see ProductService.update — so this can re-fire the next time
 * stock actually drops low again.
 */
@Component
class LowStockAlertJob(
    private val productRepository: ProductRepository,
    private val productNotifier: ProductNotifier,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(LowStockAlertJob::class.java)

    @Scheduled(fixedDelayString = "\${notifications.reminder-check-interval-ms}")
    @Transactional
    fun run() {
        val now = Instant.now()
        val due = productRepository.findLowStock(notificationProperties.lowStockThreshold)
        due.forEach { product ->
            productNotifier.lowStockAlert(product)
            product.lastLowStockAlertSentAt = now
        }
        if (due.isNotEmpty()) {
            productRepository.saveAll(due)
            log.info("Sent {} low-stock alert email(s)", due.size)
        }
    }
}
