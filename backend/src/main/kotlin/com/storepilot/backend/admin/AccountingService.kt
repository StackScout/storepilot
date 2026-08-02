package com.storepilot.backend.admin

import com.storepilot.backend.order.OrderService
import com.storepilot.backend.payout.FeeCollectionRepository
import com.storepilot.backend.payout.FeeCollectionStatus
import com.storepilot.backend.payout.PayoutRepository
import com.storepilot.backend.payout.PayoutStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Aggregates the three admin-facing money ledgers (Payout, FeeCollection,
 * Stripe settlements) into one summary — nothing here computes anything the
 * individual list endpoints don't already expose, this just sums what's
 * otherwise only visible one row at a time. Small dataset at this app's
 * scale (see PayoutService.adminList's identical findAll()-then-sort
 * pattern), so plain in-memory sums rather than SQL aggregate queries.
 */
@Service
@Transactional(readOnly = true)
class AccountingService(
    private val payoutRepository: PayoutRepository,
    private val feeCollectionRepository: FeeCollectionRepository,
    private val orderService: OrderService,
) {
    fun summary(): AccountingSummaryResponse {
        val payouts = payoutRepository.findAll()
        val feeCollections = feeCollectionRepository.findAll()
        val stripeSettlements = orderService.adminListStripeSettlements()
        return AccountingSummaryResponse(
            payoutsScheduledTotal = payouts.filter { it.status == PayoutStatus.SCHEDULED }.sumOf { it.net },
            payoutsPaidTotal = payouts.filter { it.status == PayoutStatus.PAID }.sumOf { it.net },
            feeCollectionsPendingTotal = feeCollections.filter { it.status == FeeCollectionStatus.PENDING }.sumOf { it.platformFee },
            feeCollectionsCollectedTotal = feeCollections.filter { it.status == FeeCollectionStatus.COLLECTED }.sumOf { it.platformFee },
            stripeSettledTotal = stripeSettlements.sumOf { it.total },
            stripePlatformFeeTotal = stripeSettlements.sumOf { it.platformFee },
        )
    }
}
