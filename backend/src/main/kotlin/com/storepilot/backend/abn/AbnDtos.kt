package com.storepilot.backend.abn

import com.fasterxml.jackson.annotation.JsonProperty
import com.storepilot.backend.common.WireValue

/**
 * "found" carries [AbnLookupResponse.entityName]/[AbnLookupResponse.abnStatus]
 * (and [AbnLookupResponse.gstRegistered] when known). "invalid-format" means
 * the checksum failed — never worth calling ABR for. "not-configured" means
 * no ABR_GUID is set (see AbrProperties) — expected in any environment
 * without a real GUID yet, not an error to surface loudly.
 */
enum class AbnLookupStatus(override val wireValue: String) : WireValue {
    FOUND("found"),
    INVALID_FORMAT("invalid-format"),
    NOT_FOUND("not-found"),
    NOT_CONFIGURED("not-configured"),
    ERROR("error"),
}

data class AbnLookupResponse(
    val status: String,
    val entityName: String? = null,
    val abnStatus: String? = null,
    val entityTypeName: String? = null,
    val gstRegistered: Boolean? = null,
)

/**
 * Deserialization target for the ABR "ABN Lookup" JSON endpoint
 * (abr.business.gov.au/json/AbnDetails.aspx) — field names/shape are per
 * ABR's published response format, NOT verified against a live call (no
 * GUID was available while writing this). Re-check field names against a
 * real response the first time a GUID is configured; log the raw body on
 * a parse failure rather than guessing at a fix.
 */
data class AbrRawResponse(
    @JsonProperty("Abn") val abn: String? = null,
    @JsonProperty("AbnStatus") val abnStatus: String? = null,
    @JsonProperty("EntityName") val entityName: String? = null,
    @JsonProperty("EntityTypeName") val entityTypeName: String? = null,
    @JsonProperty("Gst") val gst: String? = null,
    @JsonProperty("Message") val message: String? = null,
)
