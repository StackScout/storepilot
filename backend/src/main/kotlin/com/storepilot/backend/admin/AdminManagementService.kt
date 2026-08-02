package com.storepilot.backend.admin

import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.security.CognitoProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidPasswordException
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersInGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException

/**
 * Lets an already-authenticated admin invite another one — the in-app
 * counterpart to `infra/scripts/create-admin.sh` (which only bootstraps the
 * very first admin, out-of-band). Mirrors AuthController.register()'s
 * Cognito user-creation shape almost exactly, with two deliberate
 * differences: email_verified is set true immediately (matching
 * create-admin.sh) since only an already-admin caller can reach this at
 * all — there's no self-service signup step to verify — and the inviting
 * admin picks the initial password directly, shared with the invitee out
 * of band. That's the same permanent-password-up-front model every account
 * in this app uses; there's no temporary-password/forced-change Cognito
 * challenge flow implemented anywhere to plug a "set your own password"
 * step into.
 */
@Service
class AdminManagementService(
    private val cognitoClient: CognitoIdentityProviderClient,
    private val cognitoProperties: CognitoProperties,
    private val auditLogService: AuditLogService,
) {
    @Transactional
    fun invite(input: InviteAdminInput): InviteAdminResult {
        try {
            cognitoClient.adminCreateUser(
                AdminCreateUserRequest.builder()
                    .userPoolId(cognitoProperties.userPoolId)
                    .username(input.email)
                    .userAttributes(
                        AttributeType.builder().name("email").value(input.email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(input.name).build(),
                    )
                    .messageAction(MessageActionType.SUPPRESS)
                    .build(),
            )
        } catch (e: UsernameExistsException) {
            throw ConflictException("An account with this email already exists")
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException(e.message ?: "Password doesn't meet requirements")
        }

        cognitoClient.adminSetUserPassword(
            AdminSetUserPasswordRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(input.email)
                .password(input.password)
                .permanent(true)
                .build(),
        )
        cognitoClient.adminAddUserToGroup(
            AdminAddUserToGroupRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .username(input.email)
                .groupName("admin")
                .build(),
        )

        auditLogService.record(
            AuditAction.ADMIN_INVITED,
            "admin",
            input.email,
            "Invited new admin \"${input.name}\" (${input.email})",
        )
        return InviteAdminResult(email = input.email, name = input.name)
    }

    /** Reads Cognito's `admin` group directly rather than the local Admin table, so an invited admin who hasn't logged in yet (no JIT-provisioned row) still shows up. */
    fun list(): List<AdminSummaryResponse> {
        val result = cognitoClient.listUsersInGroup(
            ListUsersInGroupRequest.builder()
                .userPoolId(cognitoProperties.userPoolId)
                .groupName("admin")
                .build(),
        )
        return result.users().map { user ->
            val attributes = user.attributes().associate { it.name() to it.value() }
            AdminSummaryResponse(
                email = attributes["email"] ?: user.username(),
                name = attributes["name"] ?: attributes["email"] ?: user.username(),
                invitedAt = user.userCreateDate(),
            )
        }.sortedByDescending { it.invitedAt }
    }
}
