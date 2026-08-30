package com.storepilot.backend.common

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/** Public (see SecurityConfig) — the frontend fetches this instead of baking country content into NEXT_PUBLIC_* build args. */
@RestController
class PlatformConfigController(
    private val platformConfigService: PlatformConfigService,
    private val stateRepository: StateRepository,
) {
    @GetMapping("/api/platform-config")
    fun getConfig(): PlatformConfigResponse = platformConfigService.current().toResponse()

    /** State/province options for this deployment — see State.kt's doc comment. */
    @GetMapping("/api/states")
    fun getStates(): List<StateResponse> =
        stateRepository.findAllByOrderBySortOrderAscNameAsc().map { StateResponse(it.name) }

    // --- Admin-scoped (matched by SecurityConfig's /api/admin/** catch-all) ---

    /** Which payment methods this deployment offers at all — see PlatformSettings' default*Enabled doc comments. */
    @PatchMapping("/api/admin/platform-config/payment-methods")
    fun updatePaymentMethods(@RequestBody input: PlatformPaymentMethodsInput): PlatformConfigResponse =
        platformConfigService.updatePaymentMethods(input).toResponse()

    /** Whether the seller Free/Pro tier concept exists on this deployment at all — see PlatformSettings.proPlanEnabled's doc comment. */
    @PatchMapping("/api/admin/platform-config/pro-plan")
    fun updateProPlanEnabled(@RequestBody input: PlatformProPlanInput): PlatformConfigResponse =
        platformConfigService.updateProPlanEnabled(input).toResponse()
}
