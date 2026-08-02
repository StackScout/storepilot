package com.storepilot.backend.admin

/** All fields are cents, same as every other money field in this codebase. */
data class AccountingSummaryResponse(
    val payoutsScheduledTotal: Int,
    val payoutsPaidTotal: Int,
    val feeCollectionsPendingTotal: Int,
    val feeCollectionsCollectedTotal: Int,
    val stripeSettledTotal: Int,
    val stripePlatformFeeTotal: Int,
)
