package com.islandcart.backend.stripe

import com.islandcart.backend.common.ForbiddenException
import com.islandcart.backend.common.NotFoundException
import com.islandcart.backend.common.security.CurrentActor
import com.islandcart.backend.notification.NotificationProperties
import com.islandcart.backend.store.StoreRepository
import com.islandcart.backend.store.StoreSettingsRepository
import com.stripe.model.Account
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import com.stripe.model.AccountLink
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Seller-side Stripe Connect onboarding — Standard accounts (each seller
 * gets their own full, independent Stripe account; this is the account type
 * Stripe documents as carrying the least platform liability, the explicit
 * goal here — see StripeService's doc comment for the checkout side).
 * `stripeChargesEnabled`/`stripePayoutsEnabled` are never set here — only
 * ever synced from the connected account's real status via the
 * `account.updated` webhook (syncAccountStatus below), so a locally-cached
 * flag can never drift from what Stripe actually reports.
 */
@Service
class StripeConnectService(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val currentActor: CurrentActor,
    private val notificationProperties: NotificationProperties,
) {
    private val log = LoggerFactory.getLogger(StripeConnectService::class.java)

    /** POST /api/stores/{storeId}/stripe-connect/onboard */
    @Transactional
    fun startOnboarding(storeId: UUID): StripeOnboardingResponse {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }

        val accountId = settings.stripeAccountId ?: run {
            val account = Account.create(
                AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.STANDARD)
                    .setCountry("AU")
                    .setEmail(settings.contactEmail)
                    .build(),
            )
            settings.stripeAccountId = account.id
            storeSettingsRepository.save(settings)
            account.id
        }

        val returnUrl = "${notificationProperties.frontendBaseUrl}/dashboard/settings"
        val accountLink = AccountLink.create(
            AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setRefreshUrl(returnUrl)
                .setReturnUrl(returnUrl)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build(),
        )
        return StripeOnboardingResponse(onboardingUrl = accountLink.url)
    }

    /**
     * Called by StripeWebhookService for `account.updated` events — the
     * only place stripeChargesEnabled/stripePayoutsEnabled are ever
     * written. Silently ignored if the account isn't linked to any store
     * (shouldn't happen for accounts this platform created, but a stray
     * event for an unrelated account must not throw).
     */
    @Transactional
    fun syncAccountStatus(account: Account) {
        val settings = storeSettingsRepository.findByStripeAccountId(account.id)
        if (settings == null) {
            log.warn("account.updated for {} — no store settings linked to this account, ignoring", account.id)
            return
        }
        settings.stripeChargesEnabled = account.chargesEnabled ?: false
        settings.stripePayoutsEnabled = account.payoutsEnabled ?: false
        storeSettingsRepository.save(settings)
    }
}
