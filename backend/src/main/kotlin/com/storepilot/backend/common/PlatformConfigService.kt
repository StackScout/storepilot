package com.storepilot.backend.common

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Reads the single platform_settings row — the running app's live config
 * source (see PlatformSettings' doc comment). Queried fresh each call
 * rather than cached: the table is a single tiny row Postgres keeps in
 * shared_buffers, so the cost of not caching is negligible against the
 * benefit of picking up an operator's edit without a restart.
 */
@Service
@Transactional(readOnly = true)
class PlatformConfigService(
    private val repository: PlatformSettingsRepository,
) {
    fun current(): PlatformSettings =
        repository.findAll().firstOrNull()
            ?: error("platform_settings has no row — DataSeeder should have created one at startup")
}
