package com.storepilot.backend.store

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CognitoIdentity
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.notification.EmailService
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

private const val INVITE_TTL_DAYS = 7L

/**
 * Owner-side staff management + the accept-invite domain logic AuthController
 * calls into. A staff Seller is a full Seller row (see StoreStaffMember.kt's
 * doc comment) created here directly, choosing their own password via the
 * invite link — unlike AdminManagementService.invite(), where the inviting
 * admin sets the invitee's password. Mirrors EmailVerificationService's
 * "never store the raw secret" discipline for the invite token.
 */
@Service
class StoreStaffService(
    private val storeRepository: StoreRepository,
    private val storeStaffMemberRepository: StoreStaffMemberRepository,
    private val storeStaffInviteRepository: StoreStaffInviteRepository,
    private val sellerRepository: SellerRepository,
    private val storeAccessService: StoreAccessService,
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
    private val emailService: EmailService,
    private val notificationProperties: NotificationProperties,
    private val platformConfigService: PlatformConfigService,
) {
    private val log = LoggerFactory.getLogger(StoreStaffService::class.java)
    private val secureRandom = SecureRandom()

    /** POST /api/stores/{storeId}/staff/invite — owner-only. */
    @Transactional
    fun invite(storeId: UUID, input: StaffInviteInput): StoreStaffInviteResponse {
        val store = requireOwnedStore(storeId)
        if (sellerRepository.findByEmailIgnoreCase(input.email) != null) {
            throw ConflictException("An account with this email already exists")
        }
        val cognitoUserExists = try {
            cognitoClient.adminGetUser(AdminGetUserRequest.builder().userPoolId(cognitoProperties.userPoolId).username(input.email).build())
            true
        } catch (e: UserNotFoundException) {
            false
        }
        if (cognitoUserExists) throw ConflictException("An account with this email already exists")

        val rawToken = generateToken()
        val record = storeStaffInviteRepository.findByStoreIdAndEmailIgnoreCaseAndStatus(storeId, input.email, StoreStaffInviteStatus.PENDING)
            ?: StoreStaffInvite(store = store, email = input.email, name = input.name, tokenHash = hash(rawToken), expiresAt = Instant.now())
        record.name = input.name
        record.tokenHash = hash(rawToken)
        record.status = StoreStaffInviteStatus.PENDING
        record.expiresAt = Instant.now().plus(INVITE_TTL_DAYS, ChronoUnit.DAYS)
        storeStaffInviteRepository.save(record)

        val platformName = platformConfigService.current().name
        try {
            emailService.send(
                to = input.email,
                subject = "You've been invited to join ${store.name} on $platformName",
                body = buildString {
                    appendLine("Hi ${input.name},")
                    appendLine()
                    appendLine("${store.name} has invited you to help run their store on $platformName.")
                    appendLine()
                    appendLine("Accept the invite here: ${notificationProperties.frontendBaseUrl}/staff/accept-invite?token=$rawToken")
                    appendLine()
                    appendLine("This link expires in $INVITE_TTL_DAYS days.")
                },
            )
        } catch (e: Exception) {
            log.warn("Failed to send staff invite email to {} for store {} — the invite record was still saved, so a resend will work", input.email, storeId, e)
        }
        return record.toStaffInviteResponse()
    }

    /** GET /api/stores/{storeId}/staff — owner-only. */
    fun listStaff(storeId: UUID): List<StoreStaffMemberResponse> {
        requireOwnedStore(storeId)
        return storeStaffMemberRepository.findByStoreIdOrderByCreatedAtDesc(storeId).map { it.toResponse() }
    }

    /** GET /api/stores/{storeId}/staff/invites — owner-only. */
    fun listPendingInvites(storeId: UUID): List<StoreStaffInviteResponse> {
        requireOwnedStore(storeId)
        return storeStaffInviteRepository.findByStoreIdAndStatusOrderByCreatedAtDesc(storeId, StoreStaffInviteStatus.PENDING).map { it.toStaffInviteResponse() }
    }

    /**
     * DELETE /api/stores/{storeId}/staff/{staffMemberId} — owner-only. Also
     * strips the Cognito "seller" group entirely (not just this store's
     * link) — see this project's plan doc for the accepted "no smooth
     * recovery path back to ROLE_SELLER" tradeoff this implies.
     */
    @Transactional
    fun removeStaff(storeId: UUID, staffMemberId: UUID) {
        requireOwnedStore(storeId)
        val member = storeStaffMemberRepository.findById(staffMemberId).orElseThrow { NotFoundException("Staff member $staffMemberId not found") }
        if (member.store.id != storeId) throw NotFoundException("Staff member $staffMemberId not found")
        storeStaffMemberRepository.delete(member)
        try {
            cognitoClient.adminRemoveUserFromGroup(
                AdminRemoveUserFromGroupRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .username(member.seller.cognitoSub)
                    .groupName("seller")
                    .build(),
            )
        } catch (e: CognitoIdentityProviderException) {
            log.warn("Failed to remove the seller group from Cognito user {} after removing them as staff from store {} — their store access is already revoked regardless", member.seller.cognitoSub, storeId, e)
        }
    }

    /** DELETE /api/stores/{storeId}/staff/invites/{inviteId} — owner-only. */
    @Transactional
    fun revokeInvite(storeId: UUID, inviteId: UUID) {
        requireOwnedStore(storeId)
        val invite = storeStaffInviteRepository.findById(inviteId).orElseThrow { NotFoundException("Invite $inviteId not found") }
        if (invite.store.id != storeId) throw NotFoundException("Invite $inviteId not found")
        if (invite.status == StoreStaffInviteStatus.PENDING) {
            invite.status = StoreStaffInviteStatus.REVOKED
            storeStaffInviteRepository.save(invite)
        }
    }

    /** GET /api/staff/invites/{token} — public; lets the accept-invite page render before the invitee is authenticated. */
    fun getInviteDetails(rawToken: String): StaffInviteDetailsResponse {
        val invite = requirePendingInvite(rawToken)
        return StaffInviteDetailsResponse(storeName = invite.store.name, email = invite.email, name = invite.name, expiresAt = invite.expiresAt)
    }

    /** Called by AuthController.acceptStaffInvite — creates the Cognito user + Seller + StoreStaffMember, then hands back the identity for AuthController to sign in. */
    @Transactional
    fun acceptInvite(input: AcceptStaffInviteInput): CognitoIdentity {
        val invite = requirePendingInvite(input.token)
        if (sellerRepository.findByEmailIgnoreCase(invite.email) != null) {
            throw ConflictException("An account with this email already exists")
        }
        val name = input.name?.takeIf { it.isNotBlank() } ?: invite.name

        try {
            cognitoClient.adminCreateUser(
                AdminCreateUserRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .username(invite.email)
                    .userAttributes(
                        AttributeType.builder().name("email").value(invite.email).build(),
                        // The invite link itself proves ownership of this email — no separate verification-code round trip needed, same reasoning as AdminManagementService.invite().
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(name).build(),
                    )
                    .messageAction(MessageActionType.SUPPRESS)
                    .build(),
            )
        } catch (e: UsernameExistsException) {
            throw ConflictException("An account with this email already exists")
        }
        cognitoClient.adminSetUserPassword(
            AdminSetUserPasswordRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(invite.email)
                .password(input.password)
                .permanent(true)
                .build(),
        )
        grantSellerGroup(invite.email)

        val identity = cognitoClient.adminGetUser(AdminGetUserRequest.builder().userPoolId(cognitoProperties.userPoolId).username(invite.email).build())
        val sub = requireNotNull(identity.userAttributes().find { it.name() == "sub" }?.value()) { "Newly-created Cognito user has no sub attribute" }

        val seller = sellerRepository.save(Seller(cognitoSub = sub, email = invite.email, name = name))
        storeStaffMemberRepository.save(StoreStaffMember(store = invite.store, seller = seller))
        invite.status = StoreStaffInviteStatus.ACCEPTED
        storeStaffInviteRepository.save(invite)

        return CognitoIdentity(sub = sub, username = invite.email, email = invite.email, name = name)
    }

    private fun requirePendingInvite(rawToken: String): StoreStaffInvite {
        val invite = storeStaffInviteRepository.findByTokenHash(hash(rawToken)) ?: throw NotFoundException("Invite not found")
        if (invite.status != StoreStaffInviteStatus.PENDING) throw ConflictException("This invite is no longer valid")
        if (invite.expiresAt.isBefore(Instant.now())) throw ConflictException("This invite has expired — ask the store owner to send a new one")
        return invite
    }

    private fun requireOwnedStore(storeId: UUID): Store {
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        return storeAccessService.requireOwnerAccess(store)
    }

    /** Mirrors StoreService.grantSellerGroup exactly (same 2-attempt retry). */
    private fun grantSellerGroup(username: String) {
        repeat(2) { attempt ->
            try {
                cognitoClient.adminAddUserToGroup(
                    AdminAddUserToGroupRequest.builder()
                        .userPoolId(cognitoProperties.userPoolId)
                        .username(username)
                        .groupName("seller")
                        .build(),
                )
                return
            } catch (e: CognitoIdentityProviderException) {
                if (attempt == 1) throw e
            }
        }
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

private fun StoreStaffMember.toResponse() = StoreStaffMemberResponse(
    id = requireNotNull(id),
    sellerId = requireNotNull(seller.id),
    name = seller.name,
    email = seller.email,
    joinedAt = requireNotNull(createdAt),
)

private fun StoreStaffInvite.toStaffInviteResponse() = StoreStaffInviteResponse(
    id = requireNotNull(id),
    email = email,
    name = name,
    status = status.wireValue,
    invitedAt = requireNotNull(createdAt),
    expiresAt = expiresAt,
)
