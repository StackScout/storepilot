package com.islandcart.backend.common

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * Shared identity + audit columns for every entity. `createdAt` doubles as
 * the business-facing "created/joined/placed at" timestamp used throughout
 * the frontend's types (Store.joinedAt, Order.createdAt, Buyer.createdAt,
 * etc.) — there is no separate column for that, DTO mappers just read
 * `createdAt`.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null,
    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: Instant? = null,
)
