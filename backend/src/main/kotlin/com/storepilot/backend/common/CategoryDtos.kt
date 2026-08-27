package com.storepilot.backend.common

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero

data class CategoryResponse(
    val id: java.util.UUID,
    val name: String,
    val wireValue: String,
    val icon: String,
    val sortOrder: Int,
    val active: Boolean,
)

/** Used for both create and update — an admin resubmits the full shape rather than a partial patch, same convention as ProductFormInput/BookableServiceFormInput. */
data class CategoryFormInput(
    @field:NotBlank(message = "Enter a category name")
    val name: String,
    @field:NotBlank(message = "Enter a wire value")
    val wireValue: String,
    @field:NotBlank(message = "Select an icon")
    val icon: String,
    @field:PositiveOrZero(message = "Sort order must be zero or more")
    val sortOrder: Int = 0,
    val active: Boolean = true,
)

fun Category.toResponse() = CategoryResponse(
    id = requireNotNull(id),
    name = name,
    wireValue = wireValue,
    icon = icon,
    sortOrder = sortOrder,
    active = active,
)
