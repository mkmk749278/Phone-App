package com.dualshield.phone.core.model

/** The kind of number we believe we are looking at after normalization. */
enum class NumberKind {
    /** A 10-digit Indian subscriber number, with or without +91. */
    INDIAN_SUBSCRIBER,

    /** An Indian service / short-code series such as 140…, 1800…, 112. */
    INDIAN_SERVICE_CODE,

    /** A number carrying a country code other than +91. */
    INTERNATIONAL,

    /** The network gave us no number at all (CLIR / withheld). */
    PRIVATE,

    /** We have digits, but they do not fit any shape we recognise. */
    MALFORMED,
}

/**
 * The result of normalizing a raw caller ID.
 *
 * [normalized] is the canonical spelling rules match against. [matchCandidates] holds every
 * spelling a rule may legitimately match, which is what lets a single rule written as
 * `9876543210` also cover `+91 98765-43210` and `09876543210`.
 *
 * Note that no synthetic trunk-`0` spelling is ever added: doing so would make a `^09…`
 * prefix rule silently match ordinary `9…` mobile numbers.
 */
data class PhoneNumberInfo(
    val raw: String,
    val normalized: String,
    val nationalDigits: String,
    val countryCode: String?,
    val kind: NumberKind,
    val matchCandidates: List<String>,
) {
    val isPrivate: Boolean get() = kind == NumberKind.PRIVATE
    val isInternational: Boolean get() = kind == NumberKind.INTERNATIONAL
    val hasDigits: Boolean get() = normalized.isNotEmpty()
}
