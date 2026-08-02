package com.storepilot.backend.common

/** Thrown when a resource looked up by id/slug doesn't exist → 404. */
class NotFoundException(message: String) : RuntimeException(message)

/** Thrown for state conflicts (e.g. duplicate email, invalid status transition) → 409. */
class ConflictException(message: String) : RuntimeException(message)

/** Thrown when the caller isn't allowed to touch the resource (ownership check) → 403. */
class ForbiddenException(message: String) : RuntimeException(message)

/** Thrown for a failed login (wrong password, unknown email) → 401. Deliberately the same message either way — don't let this endpoint distinguish "wrong password" from "no such account" for a caller. */
class UnauthenticatedException(message: String) : RuntimeException(message)

/** Thrown at login when the credentials are correct but the account's email hasn't been verified yet → 403, distinguishable from UnauthenticatedException so the frontend can route to /verify-email instead of just showing an error. */
class EmailNotVerifiedException(message: String) : RuntimeException(message)
