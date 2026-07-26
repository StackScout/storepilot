package com.islandcart.backend.common

/** Thrown when a resource looked up by id/slug doesn't exist → 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** Thrown for state conflicts (e.g. duplicate email, invalid status transition) → 409. */
class ConflictException(message: String) : RuntimeException(message)

/** Thrown when the caller isn't allowed to touch the resource (ownership check) → 403. */
class ForbiddenException(message: String) : RuntimeException(message)
