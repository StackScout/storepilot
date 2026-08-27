package com.storepilot.backend.admin

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.security.CognitoProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import java.time.Instant

class AdminManagementServiceTest {
    private val cognitoClient = mockk<CognitoIdentityProviderClient>(relaxed = true)
    private val cognitoProperties = CognitoProperties(userPoolId = "pool-1")
    private val auditLogService = mockk<AuditLogService>(relaxed = true)

    private val service = AdminManagementService(cognitoClient, cognitoProperties, auditLogService)

    private fun inviteInput() = InviteAdminInput(name = "New Admin", email = "newadmin@example.com", password = "SuperSecret123!")

    @Test
    fun `invite rejects an email that already exists`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } throws
            UsernameExistsException.builder().message("exists").build()

        assertThrows(ConflictException::class.java) { service.invite(inviteInput()) }
    }

    @Test
    fun `invite surfaces a weak password as a client error`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } throws
            InvalidPasswordException.builder().message("Password too weak").build()

        assertThrows(IllegalArgumentException::class.java) { service.invite(inviteInput()) }
    }

    @Test
    fun `invite creates the user, sets a permanent password, grants the admin group, and audits`() {
        every { cognitoClient.adminCreateUser(any<AdminCreateUserRequest>()) } returns AdminCreateUserResponse.builder().build()

        val result = service.invite(inviteInput())

        assertEquals("newadmin@example.com", result.email)
        assertEquals("New Admin", result.name)
        verify {
            cognitoClient.adminSetUserPassword(
                match<AdminSetUserPasswordRequest> { it.password() == "SuperSecret123!" && it.permanent() },
            )
        }
        verify {
            cognitoClient.adminAddUserToGroup(
                match<AdminAddUserToGroupRequest> { it.groupName() == "admin" && it.username() == "newadmin@example.com" },
            )
        }
        verify {
            auditLogService.record(AuditAction.ADMIN_INVITED, "admin", "newadmin@example.com", any())
        }
    }

    @Test
    fun `invite marks the new admin's email as pre-verified`() {
        val slot = io.mockk.slot<AdminCreateUserRequest>()
        every { cognitoClient.adminCreateUser(capture(slot)) } returns AdminCreateUserResponse.builder().build()

        service.invite(inviteInput())

        val emailVerified = slot.captured.userAttributes().first { it.name() == "email_verified" }
        assertEquals("true", emailVerified.value())
    }

    @Test
    fun `list sorts admins by most recently invited first`() {
        val older = UserType.builder()
            .username("older@example.com")
            .userCreateDate(Instant.now().minusSeconds(3600))
            .attributes(
                AttributeType.builder().name("email").value("older@example.com").build(),
                AttributeType.builder().name("name").value("Older Admin").build(),
            )
            .build()
        val newer = UserType.builder()
            .username("newer@example.com")
            .userCreateDate(Instant.now())
            .attributes(
                AttributeType.builder().name("email").value("newer@example.com").build(),
                AttributeType.builder().name("name").value("Newer Admin").build(),
            )
            .build()
        every { cognitoClient.listUsersInGroup(any<ListUsersInGroupRequest>()) } returns
            ListUsersInGroupResponse.builder().users(older, newer).build()

        val result = service.list()

        assertEquals("newer@example.com", result.first().email)
        assertEquals("older@example.com", result.last().email)
    }

    @Test
    fun `list falls back to the username when name and email attributes are missing`() {
        val bareUser = UserType.builder()
            .username("bare-username")
            .userCreateDate(Instant.now())
            .build()
        every { cognitoClient.listUsersInGroup(any<ListUsersInGroupRequest>()) } returns
            ListUsersInGroupResponse.builder().users(bareUser).build()

        val result = service.list()

        assertEquals("bare-username", result.first().email)
        assertEquals("bare-username", result.first().name)
    }
}
