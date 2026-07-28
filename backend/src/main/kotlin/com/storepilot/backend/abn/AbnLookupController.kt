package com.storepilot.backend.abn

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

/**
 * Public — same posture as GET /api/platform-config: this only echoes back
 * public ABR register data (no ownership/session concerns), and is called
 * from the onboarding form before a seller has a store or, in some flows, a
 * session at all. Used by both onboarding (self-check) and /admin (review).
 */
@RestController
class AbnLookupController(
    private val abnLookupService: AbnLookupService,
) {
    @GetMapping("/api/abn-lookup/{abn}")
    fun lookup(@PathVariable abn: String): AbnLookupResponse = abnLookupService.lookup(abn)
}
