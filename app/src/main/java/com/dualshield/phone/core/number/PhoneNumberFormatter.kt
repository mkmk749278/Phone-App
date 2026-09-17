package com.dualshield.phone.core.number

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.model.PhoneNumberInfo

/**
 * The one place a phone number is turned into text a person reads, and the one place a
 * number is turned into the key the app stores and compares.
 *
 * Three spellings exist and are never confused with one another:
 *
 *  - **raw** — exactly what the platform handed us. Kept verbatim, never rewritten.
 *  - **canonical** — the stable identity used for dedup, grouping and cross-screen lookup.
 *    `+91 87125 82492`, `+918712582492`, `08712582492` and `8712582492` all canonicalise to
 *    the same string, which is what stops the same contact appearing four times.
 *  - **display** — the grouped, human spelling. Presentation only; it never feeds a lookup.
 *
 * Every screen formats through here, so a number cannot read one way in Recents and another
 * in Call Details.
 */
object PhoneNumberFormatter {

    const val PRIVATE_LABEL = "Private number"

    /**
     * The stable identity for a number.
     *
     * Indian subscriber numbers come back in `+91XXXXXXXXXX` form, service codes as their
     * bare digits, international numbers as `+<digits>`, and sender IDs as their token.
     * A withheld caller has no identity at all and returns an empty string — callers must
     * not treat two withheld calls as the same party.
     */
    fun canonical(raw: String?): String = canonicalOf(PhoneNumberNormalizer.normalize(raw))

    /** [canonical] for a number that has already been normalized. */
    fun canonicalOf(info: PhoneNumberInfo): String = when (info.kind) {
        NumberKind.INDIAN_SUBSCRIBER -> "+91${info.nationalDigits}"
        NumberKind.INDIAN_SERVICE_CODE -> info.normalized
        NumberKind.INTERNATIONAL -> "+${info.normalized}"
        NumberKind.ALPHANUMERIC_SENDER -> info.senderId.orEmpty()
        NumberKind.PRIVATE -> ""
        NumberKind.MALFORMED -> info.normalized
    }

    /**
     * The grouped spelling shown to the user.
     *
     * Indian mobile numbers are grouped `+91 96185 79123` because that is how they are read
     * aloud and written down here. Everything else is left ungrouped rather than guessing at
     * a convention: an invented grouping on a foreign number is worse than no grouping.
     */
    fun display(raw: String?): String = displayOf(PhoneNumberNormalizer.normalize(raw))

    /** [display] for a number that has already been normalized. */
    fun displayOf(info: PhoneNumberInfo): String = when (info.kind) {
        NumberKind.INDIAN_SUBSCRIBER ->
            "+91 ${info.nationalDigits.take(5)} ${info.nationalDigits.drop(5)}"
        NumberKind.INDIAN_SERVICE_CODE -> info.nationalDigits
        NumberKind.INTERNATIONAL -> "+${info.normalized}"
        NumberKind.ALPHANUMERIC_SENDER -> info.senderId.orEmpty()
        NumberKind.PRIVATE -> PRIVATE_LABEL
        NumberKind.MALFORMED -> info.raw
    }

    /**
     * The national spelling, for places already scoped to India where the country code is
     * noise: `96185 79123`.
     */
    fun displayNational(raw: String?): String {
        val info = PhoneNumberNormalizer.normalize(raw)
        return if (info.kind == NumberKind.INDIAN_SUBSCRIBER) {
            "${info.nationalDigits.take(5)} ${info.nationalDigits.drop(5)}"
        } else {
            displayOf(info)
        }
    }

    /**
     * The comparison key used to decide whether two numbers are the same party.
     *
     * The last ten digits, which is what makes `+91 87125 82492` and `08712582492` collapse
     * onto one another. Sender IDs key on their token instead, since they have no digits.
     * An empty key means "no identity" and must never be used to group rows together.
     */
    fun matchKey(raw: String?): String = matchKeyOf(PhoneNumberNormalizer.normalize(raw))

    /** [matchKey] for a number that has already been normalized. */
    fun matchKeyOf(info: PhoneNumberInfo): String = when (info.kind) {
        NumberKind.ALPHANUMERIC_SENDER -> info.senderId.orEmpty()
        NumberKind.PRIVATE -> ""
        else -> info.normalized.filter { it.isDigit() }.takeLast(MATCH_KEY_DIGITS)
    }

    /**
     * Ten digits is the length of an Indian subscriber number, so keying on the last ten
     * makes every domestic spelling of the same line agree while still keeping two genuinely
     * different service codes apart.
     */
    private const val MATCH_KEY_DIGITS = 10
}
