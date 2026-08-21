package com.storepilot.backend.buyer

import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.booking.BookingRepository
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.order.OrderRepository
import com.storepilot.backend.product.WishlistItemRepository
import com.storepilot.backend.store.FollowRepository
import com.storepilot.backend.store.StoreRepository
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUserGlobalSignOutRequest

/**
 * Buyer-initiated account deletion — see docs/gaps-and-assumptions.md's
 * data-subject-access entry and the approved plan this implements.
 * Deliberately its own service, not a method on BuyerService: needs a
 * different set of repository dependencies and isn't a read path.
 */
@Service
class BuyerAccountService(
    private val currentActor: CurrentActor,
    private val buyerRepository: BuyerRepository,
    private val addressRepository: AddressRepository,
    private val savedSearchRepository: SavedSearchRepository,
    private val wishlistItemRepository: WishlistItemRepository,
    private val followRepository: FollowRepository,
    private val storeRepository: StoreRepository,
    private val orderRepository: OrderRepository,
    private val bookingRepository: BookingRepository,
    private val auditLogService: AuditLogService,
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
) {
    private val log = LoggerFactory.getLogger(BuyerAccountService::class.java)

    /**
     * POST /api/me/delete — instant, self-service, no admin review (see
     * plan's reasoning). Agreed policy: anonymize order/booking history in
     * place (kept for tax/accounting retention), genuinely delete
     * everything else with no independent retention need.
     */
    @Transactional
    fun deleteCurrentBuyer() {
        val buyer = currentActor.requireBuyer()
        val buyerId = requireNotNull(buyer.id)
        val placeholderEmail = "deleted-buyer-$buyerId@storepilot.invalid"

        // Redact PII on financial/booking history — never null out `buyer`
        // itself, that would misrepresent provenance as "always was a
        // guest order". Review/Conversation/Message rows are untouched:
        // both resolve buyer name live from the Buyer FK at read time (see
        // ReviewMapper/MessagingMapper), so anonymizing the Buyer row below
        // is sufficient on its own.
        orderRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).forEach { order ->
            order.buyerEmail = placeholderEmail
            order.shipping.fullName = "Deleted user"
            order.shipping.phone = null
            order.shipping.addressLine1 = null
            order.shipping.city = null
            order.shipping.state = null
            order.shipping.postalCode = null
        }
        bookingRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).forEach { booking ->
            booking.buyerName = "Deleted user"
            booking.buyerPhone = "deleted"
            booking.buyerEmail = placeholderEmail
        }

        // Genuinely delete — no independent retention need for any of these.
        addressRepository.deleteAll(addressRepository.findByBuyerIdOrderByIsDefaultDescCreatedAtAsc(buyerId))
        savedSearchRepository.deleteAll(savedSearchRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId))
        wishlistItemRepository.deleteAll(wishlistItemRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId))
        followRepository.findByBuyerId(buyerId).forEach { follow ->
            val store = follow.store
            store.followerCount = (store.followerCount - 1).coerceAtLeast(0)
            storeRepository.save(store)
            followRepository.delete(follow)
        }

        // Audit before anonymizing, while the real identity still resolves
        // — audit_logs.actorEmail/actorId are plain snapshot columns with
        // no FK, so this durably survives the anonymization that follows.
        auditLogService.recordAsBuyer(
            buyer,
            AuditAction.BUYER_ACCOUNT_DELETED,
            "buyer",
            buyerId.toString(),
            "Buyer \"${buyer.name}\" (${buyer.email}) deleted their account",
        )

        buyer.name = "Deleted user"
        buyer.email = placeholderEmail
        buyer.phone = null
        buyer.cognitoSub = null
        buyerRepository.save(buyer)

        // Cognito's Username (required by both Admin* calls below) isn't
        // necessarily the JWT's `sub` claim — see
        // CurrentActor.fetchProfileFromCognito's doc comment — so use the
        // token's own "username" claim, same as AuthController.logout().
        // Unlike logout(), failures here are NOT swallowed: a failure must
        // surface as an error rather than silently leave a live login on an
        // already-anonymized identity. The DB anonymization above is
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
        log.info("Buyer {} deleted their account", buyerId)
    }
}
