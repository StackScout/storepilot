package com.storepilot.backend.store

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.PlatformSettings
import com.storepilot.backend.common.security.CognitoProperties
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.notification.EmailService
import com.storepilot.backend.notification.NotificationProperties
import com.storepilot.backend.seller.Seller
import com.storepilot.backend.seller.SellerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminRemoveUserFromGroupResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

class StoreStaffServiceTest {
    private val storeRepository = mockk<StoreRepository>()
    private val storeStaffMemberRepository = mockk<StoreStaffMemberRepository>()
    private val storeStaffInviteRepository = mockk<StoreStaffInviteRepository>()
    private val sellerRepository = mockk<SellerRepository>()
    private val currentActor = mockk<CurrentActor>()
    private val storeAccessService = StoreAccessService(currentActor, storeStaffMemberRepository)
    private val cognitoClient = mockk<CognitoIdentityProviderClient>()
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-1")
    private val emailService = mockk<EmailService>(relaxed = true)
    private val notificationProperties = NotificationProperties(frontendBaseUrl = "https://storepilot.au")
    private val platformConfigService = mockk<PlatformConfigService>()

    private val service = StoreStaffService(
        storeRepository,
        storeStaffMemberRepository,
        storeStaffInviteRepository,
        sellerRepository,
        storeAccessService,
        cognitoClient,
        cognitoProperties,
        emailService,
        notificationProperties,
        platformConfigService,
    )

    private val owner = Seller(cognitoSub = "owner-sub", email = "owner@example.com", name = "Owner").apply { id = UUID.randomUUID() }
    private val staff = Seller(cognitoSub = "staff-sub", email = "staff@example.com", name = "Staff").apply { id = UUID.randomUUID() }
    private lateinit var store: Store
    private val storeId: UUID get() = requireNotNull(store.id)

    @BeforeEach
    fun setUp() {
        store = Store(
            seller = owner,
            slug = "store-1",
            name = "Blue Mountains Roasters",
            tagline = "tagline",
            description = "description",
            category = "fashion",
            address = StoreAddress(city = "Sydney", state = "NSW"),
            whatsappNumber = "+61400000000",
        ).apply { id = UUID.randomUUID() }
        every { storeRepository.findById(storeId) } returns Optional.of(store)
        every { platformConfigService.current() } returns PlatformSettings(
            name = "StorePilot", tagline = "tagline", countryName = "Australia", countryCode = "AU",
            currencyCode = "AUD", currencySymbol = "$", currencyLocale = "en-AU", platformFeePercent = BigDecimal("3.5"),
            flatShippingFee = 1000, proMonthlyPriceCents = 2900, defaultCodEnabled = false, defaultOnlinePaymentEnabled = true,
            defaultBankTransferEnabled = false, proPlanEnabled = true, supportEmail = "hello@storepilot.au", companyLocation = "Sydney, Australia",
            timezone = "Australia/Sydney", returnWindowDays = 14,
        )
    }

    // ---- invite ----

    @Test
    fun `invite rejects a non-owning seller`() {
        every { currentActor.requireSeller() } returns staff
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(storeId, staff.id!!) } returns false
        assertThrows(ForbiddenException::class.java) { service.invite(storeId, StaffInviteInput(name = "New Staff", email = "new@example.com")) }
    }

    @Test
    fun `invite rejects an email that already has a Seller row`() {
        every { currentActor.requireSeller() } returns owner
        every { sellerRepository.findByEmailIgnoreCase("existing@example.com") } returns staff
        assertThrows(ConflictException::class.java) { service.invite(storeId, StaffInviteInput(name = "New Staff", email = "existing@example.com")) }
    }

    @Test
    fun `invite rejects an email that already exists in Cognito under any account type`() {
        every { currentActor.requireSeller() } returns owner
        every { sellerRepository.findByEmailIgnoreCase("buyer@example.com") } returns null
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns AdminGetUserResponse.builder().build()
        assertThrows(ConflictException::class.java) { service.invite(storeId, StaffInviteInput(name = "New Staff", email = "buyer@example.com")) }
    }

    @Test
    fun `invite creates a pending invite and emails a link containing the raw token`() {
        every { currentActor.requireSeller() } returns owner
        every { sellerRepository.findByEmailIgnoreCase("new@example.com") } returns null
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } throws UserNotFoundException.builder().message("not found").build()
        every { storeStaffInviteRepository.findByStoreIdAndEmailIgnoreCaseAndStatus(storeId, "new@example.com", StoreStaffInviteStatus.PENDING) } returns null
        val saved = slot<StoreStaffInvite>()
        every { storeStaffInviteRepository.save(capture(saved)) } answers { saved.captured.apply { id = UUID.randomUUID(); createdAt = Instant.now() } }
        val emailBody = slot<String>()
        every { emailService.send(eq("new@example.com"), any(), capture(emailBody), any()) } returns Unit

        val result = service.invite(storeId, StaffInviteInput(name = "New Staff", email = "new@example.com"))

        assertEquals("new@example.com", result.email)
        assertEquals("pending", result.status)
        assertTrue(emailBody.captured.contains("/staff/accept-invite?token="))
    }

    @Test
    fun `invite overwrites an existing pending invite instead of creating a second one`() {
        every { currentActor.requireSeller() } returns owner
        every { sellerRepository.findByEmailIgnoreCase("new@example.com") } returns null
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } throws UserNotFoundException.builder().message("not found").build()
        val existingInvite = StoreStaffInvite(store = store, email = "new@example.com", name = "Old Name", tokenHash = "old-hash", expiresAt = Instant.now())
            .apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { storeStaffInviteRepository.findByStoreIdAndEmailIgnoreCaseAndStatus(storeId, "new@example.com", StoreStaffInviteStatus.PENDING) } returns existingInvite
        every { storeStaffInviteRepository.save(any()) } answers { firstArg() }

        service.invite(storeId, StaffInviteInput(name = "New Name", email = "new@example.com"))

        assertEquals("New Name", existingInvite.name)
        verify(exactly = 1) { storeStaffInviteRepository.save(any()) }
    }

    // ---- listStaff / listPendingInvites ----

    @Test
    fun `listStaff rejects a non-owning seller`() {
        every { currentActor.requireSeller() } returns staff
        every { storeStaffMemberRepository.existsByStoreIdAndSellerId(storeId, staff.id!!) } returns false
        assertThrows(ForbiddenException::class.java) { service.listStaff(storeId) }
    }

    @Test
    fun `listStaff returns members for the owner`() {
        every { currentActor.requireSeller() } returns owner
        val member = StoreStaffMember(store = store, seller = staff).apply { id = UUID.randomUUID(); createdAt = Instant.now() }
        every { storeStaffMemberRepository.findByStoreIdOrderByCreatedAtDesc(storeId) } returns listOf(member)

        val result = service.listStaff(storeId)

        assertEquals(1, result.size)
        assertEquals(staff.email, result[0].email)
    }

    // ---- removeStaff ----

    @Test
    fun `removeStaff deletes the link and strips the Cognito seller group`() {
        every { currentActor.requireSeller() } returns owner
        val member = StoreStaffMember(store = store, seller = staff).apply { id = UUID.randomUUID() }
        every { storeStaffMemberRepository.findById(member.id!!) } returns Optional.of(member)
        every { storeStaffMemberRepository.delete(member) } returns Unit
        every { cognitoClient.adminRemoveUserFromGroup(any<AdminRemoveUserFromGroupRequest>()) } returns AdminRemoveUserFromGroupResponse.builder().build()

        service.removeStaff(storeId, member.id!!)

        verify { storeStaffMemberRepository.delete(member) }
        verify { cognitoClient.adminRemoveUserFromGroup(any<AdminRemoveUserFromGroupRequest>()) }
    }

    @Test
    fun `removeStaff 404s for a staff member belonging to a different store`() {
        every { currentActor.requireSeller() } returns owner
        val otherStore = Store(
            seller = owner, slug = "store-2", name = "Other", tagline = "t", description = "d", category = "fashion",
            address = StoreAddress(city = "Sydney", state = "NSW"), whatsappNumber = "+61400000000",
        ).apply { id = UUID.randomUUID() }
        val member = StoreStaffMember(store = otherStore, seller = staff).apply { id = UUID.randomUUID() }
        every { storeStaffMemberRepository.findById(member.id!!) } returns Optional.of(member)

        assertThrows(NotFoundException::class.java) { service.removeStaff(storeId, member.id!!) }
    }

    // ---- revokeInvite ----

    @Test
    fun `revokeInvite marks a pending invite revoked`() {
        every { currentActor.requireSeller() } returns owner
        val invite = StoreStaffInvite(store = store, email = "new@example.com", name = "New", tokenHash = "hash", expiresAt = Instant.now())
            .apply { id = UUID.randomUUID() }
        every { storeStaffInviteRepository.findById(invite.id!!) } returns Optional.of(invite)
        every { storeStaffInviteRepository.save(any()) } answers { firstArg() }

        service.revokeInvite(storeId, invite.id!!)

        assertEquals(StoreStaffInviteStatus.REVOKED, invite.status)
    }

    // ---- getInviteDetails ----

    @Test
    fun `getInviteDetails throws for an expired invite`() {
        val invite = StoreStaffInvite(
            store = store, email = "new@example.com", name = "New",
            tokenHash = sha256("raw-token"), expiresAt = Instant.now().minus(1, ChronoUnit.DAYS),
        )
        every { storeStaffInviteRepository.findByTokenHash(sha256("raw-token")) } returns invite

        assertThrows(ConflictException::class.java) { service.getInviteDetails("raw-token") }
    }

    @Test
    fun `getInviteDetails throws for an unknown token`() {
        every { storeStaffInviteRepository.findByTokenHash(any()) } returns null
        assertThrows(NotFoundException::class.java) { service.getInviteDetails("bogus-token") }
    }

    // ---- acceptInvite ----

    @Test
    fun `acceptInvite creates the Cognito user, Seller, and staff link, then marks the invite accepted`() {
        val invite = StoreStaffInvite(
            store = store, email = "new@example.com", name = "New Staff",
            tokenHash = sha256("raw-token"), expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
        )
        every { storeStaffInviteRepository.findByTokenHash(sha256("raw-token")) } returns invite
        every { sellerRepository.findByEmailIgnoreCase("new@example.com") } returns null
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } returns AdminCreateUserResponse.builder().build()
        every { cognitoClient.adminSetUserPassword(any<AdminSetUserPasswordRequest>()) } returns AdminSetUserPasswordResponse.builder().build()
        every { cognitoClient.adminAddUserToGroup(any<AdminAddUserToGroupRequest>()) } returns AdminAddUserToGroupResponse.builder().build()
        every { cognitoClient.adminGetUser(any<AdminGetUserRequest>()) } returns AdminGetUserResponse.builder()
            .userAttributes(AttributeType.builder().name("sub").value("new-sub-123").build())
            .build()
        every { sellerRepository.save(any()) } answers { (firstArg() as Seller).apply { id = UUID.randomUUID() } }
        every { storeStaffMemberRepository.save(any()) } answers { firstArg() }
        every { storeStaffInviteRepository.save(any()) } answers { firstArg() }

        val identity = service.acceptInvite(AcceptStaffInviteInput(token = "raw-token", password = "a-real-password"))

        assertEquals("new@example.com", identity.email)
        assertEquals("new-sub-123", identity.sub)
        assertEquals(StoreStaffInviteStatus.ACCEPTED, invite.status)
        verify { storeStaffMemberRepository.save(match { it.store == store }) }
    }

    @Test
    fun `acceptInvite rejects an already-accepted invite`() {
        val invite = StoreStaffInvite(
            store = store, email = "new@example.com", name = "New Staff",
            tokenHash = sha256("raw-token"), expiresAt = Instant.now().plus(1, ChronoUnit.DAYS), status = StoreStaffInviteStatus.ACCEPTED,
        )
        every { storeStaffInviteRepository.findByTokenHash(sha256("raw-token")) } returns invite

        assertThrows(ConflictException::class.java) { service.acceptInvite(AcceptStaffInviteInput(token = "raw-token", password = "a-real-password")) }
    }

    @Test
    fun `acceptInvite rejects a race where the email was registered elsewhere between invite and accept`() {
        val invite = StoreStaffInvite(
            store = store, email = "new@example.com", name = "New Staff",
            tokenHash = sha256("raw-token"), expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
        )
        every { storeStaffInviteRepository.findByTokenHash(sha256("raw-token")) } returns invite
        every { sellerRepository.findByEmailIgnoreCase("new@example.com") } returns staff

        assertThrows(ConflictException::class.java) { service.acceptInvite(AcceptStaffInviteInput(token = "raw-token", password = "a-real-password")) }
    }

    @Test
    fun `acceptInvite surfaces a Cognito username collision as a conflict`() {
        val invite = StoreStaffInvite(
            store = store, email = "new@example.com", name = "New Staff",
            tokenHash = sha256("raw-token"), expiresAt = Instant.now().plus(1, ChronoUnit.DAYS),
        )
        every { storeStaffInviteRepository.findByTokenHash(sha256("raw-token")) } returns invite
        every { sellerRepository.findByEmailIgnoreCase("new@example.com") } returns null
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } throws UsernameExistsException.builder().message("exists").build()

        assertThrows(ConflictException::class.java) { service.acceptInvite(AcceptStaffInviteInput(token = "raw-token", password = "a-real-password")) }
    }

    private fun sha256(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
