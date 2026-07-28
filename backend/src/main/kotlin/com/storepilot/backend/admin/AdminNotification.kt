package com.storepilot.backend.admin

import com.storepilot.backend.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * An admin-facing activity feed entry — currently only created when a
 * seller changes their payout bank details (see StoreService), since that's
 * the one seller action the admin can't otherwise observe (payouts happen
 * outside this app). Not tied to a specific admin account: any admin can
 * read/dismiss any notification, matching how ROLE_ADMIN itself isn't
 * per-admin-scoped anywhere else in this codebase.
 */
@Entity
@Table(name = "admin_notifications")
class AdminNotification(
    @Column(nullable = false)
    var type: AdminNotificationType,
    @Column(nullable = false, columnDefinition = "text")
    var message: String,
    @Column(name = "store_id")
    var storeId: UUID? = null,
    @Column(nullable = false)
    var read: Boolean = false,
) : BaseEntity()
