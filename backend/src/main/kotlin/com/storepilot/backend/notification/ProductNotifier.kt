package com.storepilot.backend.notification

import com.storepilot.backend.product.Product
import com.storepilot.backend.store.StoreSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/** Mirrors OrderNotifier's seller-facing receiptUploaded — resolves the recipient via StoreSettings.contactEmail, not Seller.email. */
@Component
class ProductNotifier(
    private val emailService: EmailService,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(ProductNotifier::class.java)

    fun lowStockAlert(product: Product) {
        val storeId = product.store.id ?: return
        val settings = storeSettingsRepository.findById(storeId).orElse(null)
        if (settings == null) {
            log.warn("No StoreSettings for store {} — skipping low-stock notification for product {}", storeId, product.id)
            return
        }
        sendSafely(
            to = settings.contactEmail,
            subject = "Low stock: ${product.name}",
            body = buildString {
                appendLine("${product.name} is running low — only ${product.stockQuantity} left in stock.")
                appendLine()
                appendLine("Restock it from your seller dashboard: ${productUrl(product)}")
            },
        )
    }

    /** Mirrors OrderNotifier.sendSafely — never fails the triggering operation. */
    private fun sendSafely(to: String, subject: String, body: String) {
        try {
            emailService.send(to, subject, body)
        } catch (e: Exception) {
            log.warn("Failed to send notification email to {} (subject=\"{}\") — not failing the triggering operation", to, subject, e)
        }
    }

    private fun productUrl(product: Product): String = "${notificationProperties.frontendBaseUrl}/dashboard/products/${product.id}/edit"
}
