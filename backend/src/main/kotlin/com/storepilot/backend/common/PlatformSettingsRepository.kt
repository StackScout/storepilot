package com.storepilot.backend.common

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PlatformSettingsRepository : JpaRepository<PlatformSettings, UUID>
