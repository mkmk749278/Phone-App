package com.dualshield.phone.core.search

import androidx.compose.runtime.Immutable
import com.dualshield.phone.data.system.Contact
import com.dualshield.phone.data.system.ContactNumber

/**
 * Why a result matched, in the order results are ranked.
 *
 * The order of these constants *is* the ranking rule, so a reordering here changes what the
 * user sees first. Declaration order follows the product spec: the most specific reason a
 * result could have matched comes first.
 */
enum class MatchKind {
    /** The typed digits are exactly this number. */
    NUMBER_EXACT,

    /** The number starts with the typed digits. */
    NUMBER_PREFIX,

    /** A word of the name starts with the typed digits in T9. */
    T9_NAME_PREFIX,

    /** The typed digits appear somewhere in the T9 name. */
    T9_NAME_MATCH,

    /** The typed text appears in the name. Only reachable from a text field, not the keypad. */
    TEXT_NAME_MATCH,

    /** The typed digits appear somewhere inside the number. Weakest reason to show a row. */
    NUMBER_OTHER,
}

/** One row of dialer search output: a contact, the number that matched, and why. */
@Immutable
data class DialerResult(
    val contact: Contact,
    val number: ContactNumber,
    val matchKind: MatchKind,
)

/**
 * Pre-built search index over the address book.
 *
 * Built once per contacts load, not per keystroke. Every string the search compares against
 * — the number's digits, the name lowercased, the name in T9, each word in T9 — is computed
 * here, so typing a digit costs one pass of string comparisons and no allocation-heavy work.
 * That is what keeps the dialer responsive while the user is typing.
 */
class DialerIndex private constructor(private val entries: List<Entry>) {

    /** One searchable (contact, number) pair with everything precomputed. */
    private class Entry(
        val contact: Contact,
        val number: ContactNumber,
        val numberDigits: String,
        /**
         * The national form — the last ten digits. A contact saved as `+91 96185 79123`
         * must still be found by typing `96185`, which is how anyone actually dials.
         */
        val nationalDigits: String,
        val nameLower: String,
        val nameT9: String,
        val wordsT9: List<String>,
    )

    val size: Int get() = entries.size

    /**
     * Ranked, deduplicated results for [query].
     *
     * A query of digits searches numbers and T9 names together, exactly as a dialer should.
     * A query containing letters can only have come from a text field, so it searches names.
     *
     * Results are capped at [limit] *after* ranking, so the cap never costs a better match.
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<DialerResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val digits = trimmed.filter { it.isDigit() }
        val hasLetters = trimmed.any { it.isLetter() }
        val lowered = trimmed.lowercase()

        val matches = ArrayList<Pair<Entry, MatchKind>>()
        for (entry in entries) {
            val kind = if (hasLetters) {
                entry.textMatch(lowered)
            } else {
                entry.digitMatch(digits)
            }
            if (kind != null) matches += entry to kind
        }

        return matches
            .sortedWith(
                compareBy<Pair<Entry, MatchKind>> { it.second.ordinal }
                    // Within a tier, shorter numbers first (a closer match), then by name so
                    // the list does not reshuffle arbitrarily between keystrokes.
                    .thenBy { it.first.numberDigits.length }
                    .thenBy { it.first.nameLower },
            )
            // One row per contact per distinct line. The same line stored twice under one
            // contact was already collapsed when the contact was built; this stops a contact
            // appearing once per matching number spelling here too.
            .distinctBy { (entry, _) -> entry.contact.id to entry.number.matchKey }
            .take(limit)
            .map { (entry, kind) -> DialerResult(entry.contact, entry.number, kind) }
    }

    private fun Entry.digitMatch(digits: String): MatchKind? {
        if (digits.isEmpty()) return null
        // Both spellings are tried at every tier: the user may type the number as stored
        // (with country code) or as they would dial it (without).
        return when {
            nationalDigits == digits || numberDigits == digits -> MatchKind.NUMBER_EXACT
            nationalDigits.startsWith(digits) || numberDigits.startsWith(digits) ->
                MatchKind.NUMBER_PREFIX
            wordsT9.any { it.startsWith(digits) } -> MatchKind.T9_NAME_PREFIX
            nameT9.contains(digits) -> MatchKind.T9_NAME_MATCH
            numberDigits.contains(digits) -> MatchKind.NUMBER_OTHER
            else -> null
        }
    }

    private fun Entry.textMatch(lowered: String): MatchKind? =
        if (nameLower.contains(lowered)) MatchKind.TEXT_NAME_MATCH else null

    companion object {

        /** Enough to fill the results area above the keypad without scrolling it forever. */
        const val DEFAULT_LIMIT = 20

        val EMPTY = DialerIndex(emptyList())

        fun build(contacts: List<Contact>): DialerIndex {
            if (contacts.isEmpty()) return EMPTY
            val entries = ArrayList<Entry>(contacts.size)
            for (contact in contacts) {
                val nameLower = contact.displayName.lowercase()
                val nameT9 = T9.encode(contact.displayName)
                val wordsT9 = T9.encodeWords(contact.displayName)
                for (number in contact.phoneNumbers) {
                    entries += Entry(
                        contact = contact,
                        number = number,
                        numberDigits = number.raw.filter { it.isDigit() },
                        nationalDigits = number.matchKey.filter { it.isDigit() },
                        nameLower = nameLower,
                        nameT9 = nameT9,
                        wordsT9 = wordsT9,
                    )
                }
            }
            return DialerIndex(entries)
        }
    }
}
