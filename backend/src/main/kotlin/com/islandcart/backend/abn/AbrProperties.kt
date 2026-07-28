package com.islandcart.backend.abn

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bound from ABR_GUID (application.yml). Empty-string default (same
 * bootstrap-before-configured pattern as StripeProperties) so the app boots
 * fine before a real GUID is set — [AbnLookupService] just reports the
 * lookup as "not configured" until then.
 *
 * The GUID is a free API key from the Australian Business Register's ABN
 * Lookup web services (https://abr.business.gov.au/Tools/WebServices) —
 * register with any email, it's not tied to owning a business.
 */
@ConfigurationProperties(prefix = "abr")
data class AbrProperties(
    val guid: String = "",
)
