package com.storepilot.backend.buyer

import com.storepilot.backend.common.security.CurrentActor
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Registration is handled entirely by AuthController (Cognito SignUp) —
 * there's no "create a buyer profile" endpoint anymore. The Buyer row
 * itself is JIT-provisioned by CurrentActor on first authenticated request,
 * so every method here operates on "the current caller's own buyer row",
 * never a caller-supplied id/email (that was the by-email PII leak this
 * replaced). Saved-address management lives in AddressService, not here.
 */
@Service
@Transactional(readOnly = true)
class BuyerService(
    private val currentActor: CurrentActor,
) {
    /**
     * GET /api/me — explicitly @Transactional (not the class default
     * readOnly = true): requireBuyer() may JIT-provision a new row on a
     * caller's first request, and a write nested inside a read-only
     * transaction fails at the Postgres level ("cannot execute INSERT in a
     * read-only transaction") even when the write itself runs in its own
     * REQUIRES_NEW transaction (see CurrentActor.buyerOrNull's doc comment).
     */
    @Transactional
    fun getCurrent(): BuyerResponse = currentActor.requireBuyer().toResponse()
}
