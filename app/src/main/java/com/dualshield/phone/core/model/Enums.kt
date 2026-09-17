package com.dualshield.phone.core.model

/**
 * Which SIM a rule governs.
 *
 * This is the product's central invariant: a rule scoped to [SIM2] must never influence a
 * call that arrived on SIM 1. [BOTH] exists, but the UI deliberately makes it a considered
 * choice rather than a default.
 */
enum class SimScope {
    SIM1,
    SIM2,
    BOTH;

    /** True when this scope governs the given zero-based SIM slot. */
    fun coversSlot(slotIndex: Int): Boolean = when (this) {
        SIM1 -> slotIndex == 0
        SIM2 -> slotIndex == 1
        BOTH -> slotIndex == 0 || slotIndex == 1
    }

    companion object {
        fun forSlot(slotIndex: Int): SimScope? = when (slotIndex) {
            0 -> SIM1
            1 -> SIM2
            else -> null
        }
    }
}

/** How a rule's [pattern][com.dualshield.phone.data.db.entity.CallRuleEntity.pattern] is matched. */
enum class PatternType {
    /** Whole normalized number must be equal. */
    EXACT,

    /** Normalized number must start with the pattern. */
    PREFIX,

    /** Normalized number must contain the pattern anywhere. */
    CONTAINS,

    /** Pattern is a regular expression matched against the whole normalized number. */
    REGEX,

    /** Pattern names a caller class rather than digits; see [SpecialRule]. */
    SPECIAL,

    /** Reserved for the repeated-caller detector (later phase). */
    REPEATED_CALL,
}

/** Non-numeric caller classes that a [PatternType.SPECIAL] rule can target. */
enum class SpecialRule {
    UNKNOWN_CALLER,
    PRIVATE_CALLER,
    INTERNATIONAL_CALLER;

    companion object {
        fun fromPattern(pattern: String): SpecialRule? =
            entries.firstOrNull { it.name.equals(pattern.trim(), ignoreCase = true) }
    }
}

/** What happens when a rule matches. */
enum class RuleAction {
    BLOCK,
    ALLOW,
}

/**
 * How much the app trusts a rule.
 *
 * This is surfaced in the UI so a community heuristic is never mistaken for an official
 * telecom classification.
 */
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
}

/** Where a rule came from. */
enum class Provenance {
    /** Derived from published regulatory/telecom numbering allocations. */
    OFFICIAL,

    /** Widely established in practice, though not formally published as such. */
    OFFICIAL_OR_ESTABLISHED,

    /** Crowd-sourced observation. Useful, but capable of false positives. */
    COMMUNITY,

    /** Created by the user on this device. */
    USER_DEFINED,
}

/**
 * Descriptive categories. These are deliberately accurate rather than lumping everything
 * unwanted under "spam" — 140 is a regulated promotional series, not a scam series.
 */
enum class RuleCategory(val displayName: String) {
    TELEMARKETING_PROMOTIONAL("Promotional"),
    TRANSACTIONAL_BFSI_GOVERNMENT("Transactional · BFSI/Govt"),
    TRANSACTIONAL_SERVICE_1601("Transactional · Service"),
    TOLL_FREE("Toll-free"),
    PREMIUM_RATE("Premium-rate"),
    BPO_COLLECTION_HEURISTIC("BPO / Collection"),
    VOIP_CLOUD_HEURISTIC("VoIP / Cloud"),
    UNKNOWN_CALLER("Unknown caller"),
    PRIVATE_CALLER("Private caller"),
    INTERNATIONAL("International"),
    EMERGENCY("Emergency"),
    USER_BLOCK("Blocked by you"),
    USER_ALLOW("Allowed by you"),
    OTHER("Other");

    companion object {
        fun fromName(raw: String?): RuleCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: OTHER
    }
}
