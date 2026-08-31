package com.storepilot.backend.store

import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.seller.Seller
import org.springframework.stereotype.Service

/**
 * The one place "does this seller get to touch this store" is decided,
 * now that a store can have staff on top of its owner. Every existing
 * per-service ownership helper (ProductService.requireOwnership,
 * OrderService.requireSellerOwnsOrder, etc.) delegates its body here
 * instead of re-implementing the owner-or-staff check — see this
 * project's plan doc for the full list of call sites. Financial/sensitive
 * services (StoreService.getSettings/getStats, PayoutService,
 * FeeCollectionService, StripeConnectService, BookingAnalyticsService)
 * deliberately never call into this class — they keep their own
 * unmodified, owner-only `store.seller.id == seller.id` checks.
 */
@Service
class StoreAccessService(
    private val currentActor: CurrentActor,
    private val storeStaffMemberRepository: StoreStaffMemberRepository,
) {
    fun isOperationalAccess(store: Store, seller: Seller): Boolean =
        store.seller.id == seller.id ||
            storeStaffMemberRepository.existsByStoreIdAndSellerId(requireNotNull(store.id), requireNotNull(seller.id))

    /** Owner OR staff — the gate for every operational (non-financial) seller endpoint. */
    fun requireOperationalAccess(store: Store): Store {
        val seller = currentActor.requireSeller()
        if (!isOperationalAccess(store, seller)) throw ForbiddenException("You don't have access to store ${store.id}")
        return store
    }

    /** Owner only — used by staff-management endpoints themselves (a staff member must never be able to invite/remove other staff). */
    fun requireOwnerAccess(store: Store): Store {
        val seller = currentActor.requireSeller()
        if (store.seller.id != seller.id) throw ForbiddenException("Only the store owner can do this")
        return store
    }

    /**
     * Store-agnostic — rejects a staff Seller outright, for Store-less
     * endpoints like seller billing where there's no Store to check
     * ownership against at all (a staff Seller's own plan/Stripe fields
     * are always meaningless defaults; billing is always the owner's).
     */
    fun requireOwnerSeller(): Seller {
        val seller = currentActor.requireSeller()
        if (storeStaffMemberRepository.existsBySellerId(requireNotNull(seller.id))) {
            throw ForbiddenException("Billing is managed by the store owner")
        }
        return seller
    }
}
