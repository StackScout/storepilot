package com.storepilot.backend.store

import com.storepilot.backend.admin.AdminNotificationService
import com.storepilot.backend.admin.AuditAction
import com.storepilot.backend.admin.AuditLogService
import com.storepilot.backend.common.ConflictException
import com.storepilot.backend.common.ForbiddenException
import com.storepilot.backend.common.NotFoundException
import com.storepilot.backend.common.PlatformConfigService
import com.storepilot.backend.common.security.CurrentActor
import com.storepilot.backend.common.storage.FileStorageService
import com.storepilot.backend.common.storage.FileUploadPolicies
import com.storepilot.backend.common.wireValueOf
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

/**
 * Handles the post-approval verification-change-request workflow (task
 * item 40): once a store is ACTIVE, edits to its identity-verification
 * fields (sellerType/driverLicenceNumber/abn/nicNumber/
 * businessRegistrationNumber) and their supporting documents no longer
 * apply directly — StoreService.upsertSettings/upload*Document reject them
 * outright, directing the seller here instead. Nothing a seller submits
 * takes effect on the real StoreSettings row until adminApprove() runs;
 * only one PENDING request is allowed per store at a time.
 */
@Service
@Transactional(readOnly = true)
class StoreVerificationChangeRequestService(
    private val storeRepository: StoreRepository,
    private val storeSettingsRepository: StoreSettingsRepository,
    private val changeRequestRepository: StoreVerificationChangeRequestRepository,
    private val currentActor: CurrentActor,
    private val fileStorageService: FileStorageService,
    private val platformConfigService: PlatformConfigService,
    private val auditLogService: AuditLogService,
    private val adminNotificationService: AdminNotificationService,
) {
    /** GET /api/stores/{storeId}/verification-change-requests/current — the seller's own open request, or null. */
    fun current(storeId: UUID): StoreVerificationChangeRequestResponse? {
        requireOwnedStore(storeId)
        val pending = changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING)
            ?: return null
        return pending.toResponse(storeSettingsRepository.findById(storeId).orElse(null), fileStorageService)
    }

    /**
     * POST /api/stores/{storeId}/verification-change-requests — [input]'s
     * fields are merged against the store's current live StoreSettings
     * before validating, so a submission that only changes e.g. the ABN
     * still gets validated against the seller's existing sellerType rather
     * than requiring every field to be resent.
     */
    @Transactional
    fun submit(
        storeId: UUID,
        input: VerificationChangeRequestInput,
        driverLicenceDocument: MultipartFile?,
        abnDocument: MultipartFile?,
        nicDocument: MultipartFile?,
        businessRegDocument: MultipartFile?,
    ): StoreVerificationChangeRequestResponse {
        val store = requireOwnedStore(storeId)
        if (store.verificationStatus != StoreVerificationStatus.ACTIVE) {
            throw ConflictException("Only an already-approved store can submit a verification change request — edit settings directly instead")
        }
        if (changeRequestRepository.findByStoreIdAndStatus(storeId, StoreVerificationChangeRequestStatus.PENDING) != null) {
            throw ConflictException("A verification change request is already pending review for this store")
        }
        val hasTextChange = input.sellerType != null || input.driverLicenceNumber != null || input.abn != null ||
            input.nicNumber != null || input.businessRegistrationNumber != null
        val hasFileChange = driverLicenceDocument != null || abnDocument != null || nicDocument != null || businessRegDocument != null
        require(hasTextChange || hasFileChange) { "Include at least one changed field or document" }

        val current = storeSettingsRepository.findById(storeId).orElseThrow {
            NotFoundException("No settings for store $storeId yet")
        }
        val proposedSellerType = input.sellerType?.let { wireValueOf<SellerType>(it) } ?: current.sellerType
        requireCountryVerificationFields(
            platformConfigService.current().countryCode,
            proposedSellerType,
            input.driverLicenceNumber ?: current.driverLicenceNumber,
            input.abn ?: current.abn,
            input.nicNumber ?: current.nicNumber,
            input.businessRegistrationNumber ?: current.businessRegistrationNumber,
            current.gstRegistered,
        )

        val request = StoreVerificationChangeRequest(
            store = store,
            sellerType = input.sellerType?.let { wireValueOf<SellerType>(it) },
            driverLicenceNumber = input.driverLicenceNumber,
            abn = input.abn,
            nicNumber = input.nicNumber,
            businessRegistrationNumber = input.businessRegistrationNumber,
            driverLicenceDocumentUrl = driverLicenceDocument?.let { storeDocument(it) },
            abnDocumentUrl = abnDocument?.let { storeDocument(it) },
            nicDocumentUrl = nicDocument?.let { storeDocument(it) },
            businessRegDocumentUrl = businessRegDocument?.let { storeDocument(it) },
        )
        val saved = changeRequestRepository.save(request)

        auditLogService.recordAsSeller(
            currentActor.requireSeller(),
            AuditAction.STORE_VERIFICATION_CHANGE_REQUESTED,
            "store",
            storeId.toString(),
            "Requested a verification change for \"${store.name}\"",
        )
        adminNotificationService.notifyVerificationChangeRequested(store)
        return saved.toResponse(current, fileStorageService)
    }

    private fun storeDocument(file: MultipartFile): String =
        fileStorageService.store(
            "seller-documents",
            file,
            FileUploadPolicies.DOCUMENT_CONTENT_TYPES,
            FileUploadPolicies.DOCUMENT_MAX_BYTES,
        )

    private fun requireOwnedStore(storeId: UUID): Store {
        val seller = currentActor.requireSeller()
        val store = storeRepository.findById(storeId).orElseThrow { NotFoundException("Store $storeId not found") }
        if (store.seller.id != seller.id) throw ForbiddenException("You don't own store $storeId")
        return store
    }

    // --- Admin — gated by SecurityConfig's hasRole("ADMIN") on /api/admin/** ---

    fun adminList(status: String?): List<StoreVerificationChangeRequestResponse> {
        val statusEnum = status?.let { wireValueOf<StoreVerificationChangeRequestStatus>(it) }
        val requests = if (statusEnum != null) {
            changeRequestRepository.findByStatusOrderByCreatedAtDesc(statusEnum)
        } else {
            changeRequestRepository.findAllByOrderByCreatedAtDesc()
        }
        return requests.map { it.toResponse(storeSettingsRepository.findById(requireNotNull(it.store.id)).orElse(null), fileStorageService) }
    }

    /** Applies every proposed field/document onto the real StoreSettings row, then closes the request. */
    @Transactional
    fun adminApprove(requestId: UUID): StoreSettingsResponse {
        val request = requireOpenRequest(requestId)
        val storeId = requireNotNull(request.store.id)
        val settings = storeSettingsRepository.findById(storeId).orElseThrow { NotFoundException("No settings for store $storeId") }

        request.sellerType?.let { settings.sellerType = it }
        request.driverLicenceNumber?.let { settings.driverLicenceNumber = it }
        request.abn?.let { settings.abn = it }
        request.nicNumber?.let { settings.nicNumber = it }
        request.businessRegistrationNumber?.let { settings.businessRegistrationNumber = it }
        request.driverLicenceDocumentUrl?.let { settings.driverLicenceDocumentUrl = it }
        request.abnDocumentUrl?.let { settings.abnDocumentUrl = it }
        request.nicDocumentUrl?.let { settings.nicDocumentUrl = it }
        request.businessRegDocumentUrl?.let { settings.businessRegDocumentUrl = it }
        // Defensive re-validation — should already hold from submit()'s own
        // check, but the deployment's country could theoretically have
        // changed between submission and review.
        requireCountryVerificationFields(
            platformConfigService.current().countryCode,
            settings.sellerType,
            settings.driverLicenceNumber,
            settings.abn,
            settings.nicNumber,
            settings.businessRegistrationNumber,
            settings.gstRegistered,
        )
        val savedSettings = storeSettingsRepository.save(settings)

        val admin = currentActor.requireAdmin()
        request.status = StoreVerificationChangeRequestStatus.APPROVED
        request.reviewedAt = Instant.now()
        request.reviewedByEmail = admin.email
        changeRequestRepository.save(request)

        auditLogService.record(
            AuditAction.STORE_VERIFICATION_CHANGE_APPROVED,
            "store",
            storeId.toString(),
            "Approved a verification change for \"${request.store.name}\"",
        )
        return savedSettings.toResponse(fileStorageService)
    }

    @Transactional
    fun adminReject(requestId: UUID, input: VerificationChangeRequestReviewInput): StoreVerificationChangeRequestResponse {
        require(!input.rejectionReason.isNullOrBlank()) { "A rejection reason is required" }
        val request = requireOpenRequest(requestId)
        val storeId = requireNotNull(request.store.id)

        val admin = currentActor.requireAdmin()
        request.status = StoreVerificationChangeRequestStatus.REJECTED
        request.rejectionReason = input.rejectionReason
        request.reviewedAt = Instant.now()
        request.reviewedByEmail = admin.email
        val saved = changeRequestRepository.save(request)

        auditLogService.record(
            AuditAction.STORE_VERIFICATION_CHANGE_REJECTED,
            "store",
            storeId.toString(),
            "Rejected a verification change for \"${request.store.name}\": ${input.rejectionReason}",
        )
        return saved.toResponse(storeSettingsRepository.findById(storeId).orElse(null), fileStorageService)
    }

    private fun requireOpenRequest(requestId: UUID): StoreVerificationChangeRequest {
        val request = changeRequestRepository.findById(requestId).orElseThrow { NotFoundException("Change request $requestId not found") }
        if (request.status != StoreVerificationChangeRequestStatus.PENDING) {
            throw ConflictException("This change request has already been reviewed")
        }
        return request
    }
}
