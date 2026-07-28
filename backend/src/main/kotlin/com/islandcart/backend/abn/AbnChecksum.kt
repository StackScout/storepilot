package com.islandcart.backend.abn

/** ABR's published weighting factors for the modulus-89 check-digit algorithm below, one per digit position. */
private val WEIGHTS = intArrayOf(10, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19)

/**
 * Validates an ABN's check digit per the algorithm published by the
 * Australian Business Register — catches typos instantly with no network
 * call. Does NOT confirm the ABN is actually registered/active; that needs
 * [AbnLookupService]'s live ABR Lookup call.
 */
fun isValidAbnChecksum(rawAbn: String): Boolean {
    val digits = rawAbn.filter { it.isDigit() }
    if (digits.length != 11) return false

    val weightedSum = digits.mapIndexed { index, char ->
        val digit = char.digitToInt()
        val adjusted = if (index == 0) digit - 1 else digit
        adjusted * WEIGHTS[index]
    }.sum()

    return weightedSum % 89 == 0
}
