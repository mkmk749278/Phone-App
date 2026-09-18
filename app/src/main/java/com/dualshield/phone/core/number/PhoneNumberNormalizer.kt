package com.dualshield.phone.core.number

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.model.PhoneNumberInfo

/**
 * Turns whatever the network handed us into a shape rules can match against.
 *
 * Two behaviours here are deliberate and worth keeping:
 *
 *  1. The Indian trunk `0` is stripped **only** when the remainder is a plausible 10-digit
 *     subscriber number. `0900123456` therefore keeps its leading zero, so the official
 *     `^0900` premium-rate rule still matches instead of being silently defanged.
 *  2. International numbers are never rewritten into Indian shape. A `+1` number stays a
 *     `+1` number, so an India-specific rule cannot accidentally reach it.
 *
 * Everything is pure and side-effect free so the whole thing is unit-testable off-device.
 */
object PhoneNumberNormalizer {

    private const val INDIA_CC = "91"

    /** Country codes we can recognise well enough to strip. Longest match wins. */
    private val KNOWN_COUNTRY_CODES: Set<String> = setOf(
        // 3-digit
        "971", "966", "965", "968", "974", "973", "977", "880", "960", "994", "998",
        "212", "234", "254", "255", "256", "260", "263", "351", "352", "353", "354",
        "355", "356", "357", "358", "359", "370", "371", "372", "373", "374", "375",
        "376", "377", "378", "380", "381", "382", "385", "386", "387", "389", "420",
        "421", "423", "852", "853", "855", "856", "886", "960", "961", "962", "963",
        "964", "967", "970", "972",
        // 2-digit
        "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44",
        "45", "46", "47", "48", "49", "51", "52", "53", "54", "55", "56", "57", "58",
        "60", "61", "62", "63", "64", "65", "66", "81", "82", "84", "86", "90", "91",
        "92", "93", "94", "95", "98",
        // 1-digit
        "1", "7",
    )

    /**
     * Numbers that must reach the user no matter what any rule says.
     *
     * Kept separate from every "spam" category on purpose — these are safety-of-life
     * numbers and must never be swept up by a prefix or heuristic rule.
     */
    val EMERGENCY_NUMBERS: Set<String> = setOf(
        "112", // Pan-India emergency
        "100", // Police
        "101", // Fire
        "102", // Ambulance
        "108", // Emergency response
        "104", // Health helpline
        "1091", // Women's helpline
        "1098", // Childline
        "1073", // Road accident
        "1070", // Disaster management
        "1930", // Cyber financial fraud
        "181", // Women's helpline (state)
    )

    /**
     * Normalizes [raw]. Never throws: an unparseable input comes back as
     * [NumberKind.MALFORMED] or [NumberKind.PRIVATE], both of which the rule engine
     * treats as "allow unless the user explicitly asked otherwise".
     */
    fun normalize(raw: String?): PhoneNumberInfo {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty() || isWithheldMarker(source)) {
            return PhoneNumberInfo(
                raw = source,
                normalized = "",
                nationalDigits = "",
                countryCode = null,
                kind = NumberKind.PRIVATE,
                matchCandidates = emptyList(),
            )
        }

        // Keep only digits, remembering whether the caller ID was explicitly international.
        val hadPlus = source.startsWith("+")
        var digits = source.filter { it.isDigit() }
        if (digits.isEmpty()) {
            // Letters but no digits at all is an SMS sender ID (`AXISBK`, `VM-HDFCBK`), not a
            // withheld caller. The test is deliberately strict — a source carrying any digit
            // stays on the numeric path below, so a vanity or partially-lettered dial string
            // is normalized exactly as it always was.
            val sender = senderIdOf(source)
            return PhoneNumberInfo(
                raw = source,
                normalized = "",
                nationalDigits = "",
                countryCode = null,
                kind = if (sender != null) NumberKind.ALPHANUMERIC_SENDER else NumberKind.PRIVATE,
                matchCandidates = emptyList(),
                senderId = sender,
            )
        }

        // `00` is the international access code; treat it exactly like a leading `+`.
        var international = hadPlus
        if (!international && digits.length > 4 && digits.startsWith("00")) {
            digits = digits.drop(2)
            international = true
        }

        // Indian country code, however it was written.
        if (digits.startsWith(INDIA_CC) && (international || digits.length >= 12)) {
            val rest = digits.drop(INDIA_CC.length)
            if (rest.isNotEmpty()) {
                return indian(source, rest, explicitCountryCode = true)
            }
        }

        if (international) {
            val cc = detectCountryCode(digits)
            val national = cc?.let { digits.drop(it.length) } ?: digits
            return PhoneNumberInfo(
                raw = source,
                normalized = digits,
                nationalDigits = national,
                countryCode = cc,
                kind = NumberKind.INTERNATIONAL,
                matchCandidates = candidatesOf(digits, national),
            )
        }

        return indian(source, digits, explicitCountryCode = false)
    }

    /** Convenience for call sites that only need the canonical string. */
    fun normalizedOrEmpty(raw: String?): String = normalize(raw).normalized

    /** True when this number must bypass every block rule. */
    fun isEmergency(info: PhoneNumberInfo): Boolean =
        info.matchCandidates.any { it in EMERGENCY_NUMBERS }

    private fun indian(
        source: String,
        digitsIn: String,
        explicitCountryCode: Boolean,
    ): PhoneNumberInfo {
        var digits = digitsIn

        // Strip the national trunk prefix only when it genuinely is one: `0` followed by a
        // 10-digit subscriber number beginning 6-9. `0900…` is left intact.
        if (digits.length == 11 &&
            digits.startsWith("0") &&
            digits[1] in '6'..'9'
        ) {
            digits = digits.drop(1)
        }

        val kind = when {
            digits.length == 10 && digits[0] in '6'..'9' -> NumberKind.INDIAN_SUBSCRIBER
            isServiceCode(digits) -> NumberKind.INDIAN_SERVICE_CODE
            digits.length in 3..12 -> NumberKind.INDIAN_SERVICE_CODE
            else -> NumberKind.MALFORMED
        }

        return PhoneNumberInfo(
            raw = source,
            normalized = digits,
            nationalDigits = digits,
            countryCode = if (explicitCountryCode) INDIA_CC else null,
            kind = kind,
            matchCandidates = candidatesOf(digits, digits),
        )
    }

    /**
     * Indian national-level service numbering: anything in the `1xx`/`14xx`/`16xx`/`18xx`
     * families, plus the `0900` premium-rate series, plus short helplines.
     */
    private fun isServiceCode(digits: String): Boolean = when {
        digits.length < 3 -> false
        digits.startsWith("0900") -> true
        digits.startsWith("1") -> true
        digits.length <= 6 -> true
        else -> false
    }

    private fun detectCountryCode(digits: String): String? {
        for (length in 3 downTo 1) {
            if (digits.length <= length) continue
            val candidate = digits.take(length)
            if (candidate in KNOWN_COUNTRY_CODES) return candidate
        }
        return null
    }

    private fun candidatesOf(normalized: String, national: String): List<String> =
        buildList {
            if (normalized.isNotEmpty()) add(normalized)
            if (national.isNotEmpty() && national != normalized) add(national)
        }

    /**
     * The sender token for an alphanumeric SMS address, or null when [source] carries no
     * letters at all.
     *
     * Indian DLT sender IDs arrive with an operator/route prefix attached — `VM-AXISBK`,
     * `AD-HDFCBK`, `TX-SBIINB`. The trailing token is the part the user recognises, so that
     * is what is kept; the two-letter route prefix is dropped.
     */
    private fun senderIdOf(source: String): String? {
        if (source.none { it.isLetter() }) return null
        val cleaned = source.uppercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val tokens = cleaned.split('-', '_').filter { it.isNotBlank() && it.any(Char::isLetter) }
        if (tokens.isEmpty()) return null

        // The token with the most letters is the sender. Taking the *last* token was wrong,
        // and wrong in the worst way: it worked on the old two-part form (`VM-AXISBK`) and
        // silently destroyed the new three-part one. TRAI headers now carry a content
        // category as a final single letter — `AX-SBIINB-S` for service, `-T`
        // transactional, `-P` promotional, `-G` government — so every bank, operator and
        // government message in the list was titled `S`, `T`, `P` or `G`.
        //
        // Choosing by letter count rather than by position handles both forms without
        // needing a list of route prefixes or category letters to keep up to date: a
        // two-letter route prefix and a one-letter category can never outweigh the name
        // itself. On a tie the later token wins, since the route prefix comes first.
        return tokens.maxByOrNull { token -> token.count(Char::isLetter) * 100 + token.length }
            ?.let { best -> tokens.last { it.count(Char::isLetter) == best.count(Char::isLetter) } }
    }

    private fun isWithheldMarker(source: String): Boolean {
        val lowered = source.lowercase()
        return lowered in WITHHELD_MARKERS
    }

    private val WITHHELD_MARKERS = setOf(
        "unknown", "private", "restricted", "withheld", "anonymous", "blocked",
        "-1", "-2", "-3", "p", "unavailable",
    )
}
