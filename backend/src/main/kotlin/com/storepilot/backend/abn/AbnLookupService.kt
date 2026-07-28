package com.storepilot.backend.abn

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Wraps the ABR "ABN Lookup" free web service (abr.business.gov.au) —
 * confirms an ABN is actually registered and pulls back the entity name for
 * the seller/admin to eyeball against what was typed. Requires a free GUID
 * from https://abr.business.gov.au/Tools/WebServices (see AbrProperties);
 * with no GUID configured this always reports [AbnLookupStatus.NOT_CONFIGURED]
 * rather than failing — same "boots fine before real creds exist" posture as
 * StripeService.
 *
 * NOT verified against a live call — written from ABR's documented response
 * shape with no GUID available to test against. The first real lookup once
 * a GUID is set should be checked by hand; if entity names come back empty
 * for a known-good ABN, check the raw body logged on parse failure before
 * assuming the checksum or request is wrong.
 */
@Service
class AbnLookupService(
    private val abrProperties: AbrProperties,
) {
    private val log = LoggerFactory.getLogger(AbnLookupService::class.java)
    private val restClient = RestClient.create()

    fun lookup(rawAbn: String): AbnLookupResponse {
        if (!isValidAbnChecksum(rawAbn)) {
            return AbnLookupResponse(status = AbnLookupStatus.INVALID_FORMAT.wireValue)
        }
        if (abrProperties.guid.isBlank()) {
            return AbnLookupResponse(status = AbnLookupStatus.NOT_CONFIGURED.wireValue)
        }

        val digits = rawAbn.filter { it.isDigit() }
        return try {
            val raw = restClient.get()
                .uri("https://abr.business.gov.au/json/AbnDetails.aspx?abn={abn}&guid={guid}", digits, abrProperties.guid)
                .retrieve()
                .body(AbrRawResponse::class.java)

            if (raw == null || raw.entityName.isNullOrBlank()) {
                log.warn("ABN lookup for {} returned no entity name (message: {})", digits, raw?.message)
                AbnLookupResponse(status = AbnLookupStatus.NOT_FOUND.wireValue)
            } else {
                AbnLookupResponse(
                    status = AbnLookupStatus.FOUND.wireValue,
                    entityName = raw.entityName,
                    abnStatus = raw.abnStatus,
                    entityTypeName = raw.entityTypeName,
                    gstRegistered = !raw.gst.isNullOrBlank(),
                )
            }
        } catch (e: RestClientException) {
            log.warn("ABN lookup for {} failed", digits, e)
            AbnLookupResponse(status = AbnLookupStatus.ERROR.wireValue)
        }
    }
}
