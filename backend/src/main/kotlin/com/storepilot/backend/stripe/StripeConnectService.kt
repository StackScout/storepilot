package com.storepilot.backend.stripe

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.store.StoreRepository
import com.storepilot.backend.store.StoreSettingsRepository
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
     * POST /api/stores/{storeId}/stripe-connect/refresh — pulls the
     * connected account's live status directly from Stripe and syncs it,
     * the same way the `account.updated` webhook does. Exists because that
     * webhook only arrives if the Stripe Dashboard endpoint is correctly
     * configured to listen to **connected-account** events (see
     * StripeController's doc comment) — if that's ever misconfigured, wrong,
     * or an individual event is dropped, a seller would otherwise be stuck
     * seeing "Finish onboarding" forever with no way to unstick themselves.
     * Safe to call anytime; a no-op if nothing has actually changed.
     */
    @Transactional
    fun refreshAccountStatus(storeId: UUID) {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        val settings = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        val accountId = settings.stripeAccountId ?: throw ConflictException("No Stripe account connected yet")
        syncAccountStatus(Account.retrieve(accountId))
    }

    /**
     * Called by StripeWebhookService for `account.updated` events, and by
     * refreshAccountStatus above — the only two places
     * stripeChargesEnabled/stripePayoutsEnabled are ever written. Silently
     * ignored if the account isn't linked to any store (shouldn't happen
     * for accounts this platform created, but a stray event for an
     * unrelated account must not throw).
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
