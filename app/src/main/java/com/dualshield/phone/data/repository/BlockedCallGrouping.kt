package com.dualshield.phone.data.repository

import androidx.compose.runtime.Immutable
import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.data.db.entity.BlockedCallEntity

/**
 * Every blocked attempt from one number, collapsed into a single row.
 *
 * A number that tried seventeen times is one entry saying seventeen, not seventeen entries.
 * The individual attempts are still here, in [records], for the detail view — nothing is
 * thrown away, it is just not all shown at once.
 */
@Immutable
data class BlockedCallGroup(
    val matchKey: String,
    val displayNumber: String,
    val displayName: String?,
    val attempts: Int,
    val latestTimestamp: Long,
    val reason: String,
    val ruleName: String,
    val simLabel: String,
    /** Every attempt, newest first. */
    val records: List<BlockedCallEntity>,
) {
    /** The line shown to the user: a name when there is one, otherwise the number. */
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: displayNumber

    /** "(17)" next to the number, the way the reference dialer shows it. */
    val attemptsLabel: String get() = if (attempts > 1) "($attempts)" else ""
}

/**
 * Collapses blocked-call records into one group per caller.
 *
 * Grouping is by canonical match key, so the same line blocked under two different spellings
 * is one caller rather than two — the same rule that stops duplicate contacts, applied to
 * blocked history.
 *
 * A record with no usable key (a withheld caller) cannot be grouped with anything, so each
 * one stays its own entry. Two withheld calls are not evidence of the same party.
 */
fun groupBlockedCalls(records: List<BlockedCallEntity>): List<BlockedCallGroup> {
    if (records.isEmpty()) return emptyList()

    val grouped = LinkedHashMap<String, MutableList<BlockedCallEntity>>()
    records.forEach { record ->
        val key = PhoneNumberFormatter.matchKey(record.rawNumber)
            .ifEmpty { "id:${record.id}" }
        grouped.getOrPut(key) { mutableListOf() }.add(record)
    }

    return grouped.map { (key, entries) ->
        val ordered = entries.sortedByDescending { it.timestamp }
        val newest = ordered.first()
        BlockedCallGroup(
            matchKey = key,
            displayNumber = PhoneNumberFormatter.display(newest.rawNumber),
            // The most recent record's name wins: if a number was saved to contacts since
            // the first block, the name the user knows it by is the newer one.
            displayName = ordered.firstNotNullOfOrNull { it.displayName?.takeIf(String::isNotBlank) },
            attempts = ordered.size,
            latestTimestamp = newest.timestamp,
            reason = newest.reason,
            ruleName = newest.matchedRuleName,
            simLabel = newest.simLabel,
            records = ordered,
        )
    }.sortedByDescending { it.latestTimestamp }
}
