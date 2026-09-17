package com.dualshield.phone.core.model

/** The kind of number we believe we are looking at after normalization. */
enum class NumberKind {
    /** A 10-digit Indian subscriber number, with or without +91. */
    INDIAN_SUBSCRIBER,

    /** An Indian service / short-code series such as 140…, 1800…, 112. */
    INDIAN_SERVICE_CODE,

    /** A number carrying a country code other than +91. */
    INTERNATIONAL,

    /**
     * An alphanumeric sender ID, as used by banks and operators for SMS: `AXISBK`, `JIO`,
     * `VM-HDFCBK`.
     *
     * This is deliberately distinct from [PRIVATE]. These senders were previously folded
     * into [PRIVATE] and therefore surfaced to the user as "Private number", which is wrong
     * twice over: nothing is withheld, and the sender is usually a business the user
     * recognises by name.
     */
    ALPHANUMERIC_SENDER,

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
    /**
     * The cleaned-up sender token for an [NumberKind.ALPHANUMERIC_SENDER], such as `AXISBK`
     * from `VM-AXISBK`. Null for everything else.
     *
     * Held separately from [normalized] on purpose: [normalized] is the digit string the
     * rule engine matches patterns against, and putting letters in it would let a numeric
     * rule reason about a value that has no digits at all.
     */
    val senderId: String? = null,
) {
    val isPrivate: Boolean get() = kind == NumberKind.PRIVATE
    val isInternational: Boolean get() = kind == NumberKind.INTERNATIONAL
    val isAlphanumericSender: Boolean get() = kind == NumberKind.ALPHANUMERIC_SENDER
    val hasDigits: Boolean get() = normalized.isNotEmpty()
}
