package com.storepilot.backend.seller

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettings
import com.storepilot.backend.store.StoreSettingsRepository
import com.storepilot.backend.store.StoreVerificationStatus
import com.storepilot.backend.stripe.StripeConnectService
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest

private const val REDACTED = "[redacted]"

/**
 * Seller-initiated account deletion — the heavier of the two
 * data-subject-deletion flows (see BuyerAccountService for the buyer side
 * and the approved plan both implement). Only reachable once the seller's
 * store is already `CLOSED` (see StoreService.closeStore) — that two-step
 * split is the safety mechanism in place of an admin reviewer, per the
 * plan's reasoning.
 */
@Service
class SellerAccountService(
    private val currentActor: CurrentActor,
    private val sellerRepository: SellerRepository,
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val sellerBillingService: SellerBillingService,
    private val stripeConnectService: StripeConnectService,
    private val fileStorageService: FileStorageService,
    private val auditLogService: AuditLogService,
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
) {
    private val log = LoggerFactory.getLogger(SellerAccountService::class.java)

    /**
     * POST /api/me/seller/delete — instant, self-service, no admin review
     * (see plan's reasoning: the close-store precondition checks already
     * catch everything an admin reviewer would exist to catch). Each Stripe
     * step below is naturally idempotent — cancelling an already-cancelled
     * subscription, deleting an already-deleted customer, deauthorizing an
     * already-deauthorized Connect account are all treated by Stripe as
     * already done, not hard failures — so a partial mid-sequence failure
     * is safe to retry rather than an escalation.
     */
    @Transactional
    fun deleteCurrentSeller() {
        val seller = currentActor.requireSeller()
        val sellerId = requireNotNull(seller.id)
        val store = storeRepository.findBySellerId(sellerId)
        if (store != null && store.verificationStatus != StoreVerificationStatus.CLOSED) {
            throw ConflictException("Close your store before deleting your account")
        }

        sellerBillingService.cancelAndDeleteCustomer(seller)

        val settings = store?.let { storeSettingsRepository.findById(requireNotNull(it.id)).orElse(null) }
        settings?.stripeAccountId?.let { accountId ->
            runCatching { stripeConnectService.deauthorize(accountId) }
                .onFailure { log.warn("Failed to deauthorize Stripe Connect account {} for seller {} — continuing deletion", accountId, sellerId, it) }
        }

        // Audit before anonymizing, while the real identity still resolves
        // — audit_logs.actorEmail/actorId are plain snapshot columns with
        // no FK, so this durably survives the anonymization that follows.
        auditLogService.recordAsSeller(
            seller,
            AuditAction.SELLER_ACCOUNT_DELETED,
            "seller",
            sellerId.toString(),
            "Seller \"${seller.name}\" (${seller.email}) deleted their account" +
                (store?.let { " and closed store \"${it.name}\"" } ?: ""),
        )

        settings?.let { anonymizeStoreSettings(it) }

        seller.name = "Deleted seller"
        seller.email = "deleted-seller-$sellerId@storepilot.invalid"
        seller.cognitoSub = "deleted-$sellerId"
        seller.stripeCustomerId = null
        seller.stripeSubscriptionId = null
        seller.plan = SellerPlan.FREE
        seller.planCancelAtPeriodEnd = false
        sellerRepository.save(seller)

        // Same non-swallowed-failure rule as BuyerAccountService — a
        // failure here must surface as an error, not silently leave a live
        // login on an already-anonymized identity. Every write above is
        // idempotent, so retrying this whole call after a partial Cognito
        // failure is safe.
        val auth = SecurityContextHolder.getContext().authentication as JwtAuthenticationToken
        val username = auth.token.getClaimAsString("username") ?: auth.token.subject
        cognitoClient.adminUserGlobalSignOut(
            AdminUserGlobalSignOutRequest.builder().userPoolId(cognitoProperties.userPoolId).username(username).build(),
        )
        cognitoClient.adminDeleteUser(
            AdminDeleteUserRequest.builder().userPoolId(cognitoProperties.userPoolId).username(username).build(),
        )
        log.info("Seller {} deleted their account", sellerId)
    }

    /** Deletes the underlying document files before nulling their pointers — never just orphan them. Operational toggles (codEnabled, stripeEnabled, gstRegistered, transactionFeePercent, sellerType, ...) are left untouched — moot once the store is closed, no privacy purpose in touching them. */
    private fun anonymizeStoreSettings(settings: StoreSettings) {
        settings.contactEmail = REDACTED
        settings.contactPhone = REDACTED
        settings.bankAccountName = REDACTED
        settings.bankAccountNumber = REDACTED
        settings.bankName = REDACTED
        settings.driverLicenceNumber = null
        settings.abn = null
        settings.nicNumber = null
        settings.businessRegistrationNumber = null

        listOfNotNull(
            settings.driverLicenceDocumentUrl,
            settings.abnDocumentUrl,
            settings.nicDocumentUrl,
            settings.businessRegDocumentUrl,
        ).forEach { runCatching { fileStorageService.delete(it) } }
        settings.driverLicenceDocumentUrl = null
        settings.abnDocumentUrl = null
        settings.nicDocumentUrl = null
        settings.businessRegDocumentUrl = null

        storeSettingsRepository.save(settings)
    }
}
