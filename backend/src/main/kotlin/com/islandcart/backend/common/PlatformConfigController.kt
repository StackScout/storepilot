package com.islandcart.backend.common

import org.springframework.web.bind.annotation.GetMapping
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
}
