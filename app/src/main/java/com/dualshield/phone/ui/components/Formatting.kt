package com.dualshield.phone.ui.components

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatting helpers shared by every screen, kept out of composables so they stay testable. */
object Formatting {

    private class Formats(val locale: Locale) {
        val time: SimpleDateFormat = SimpleDateFormat("h:mm a", locale)
        val day: SimpleDateFormat = SimpleDateFormat("d MMM", locale)
        val full: SimpleDateFormat = SimpleDateFormat("d MMM yyyy · h:mm a", locale)
    }

    /**
     * Formatters are rebuilt when the device locale changes, but read without locking.
     *
     * This runs once per row per frame while a list is flung, so a `@Synchronized` read
     * here showed up as contention. A volatile reference costs nothing, and the worst a
     * race can do is build one extra Formats instance.
     */
    @Volatile
    private var formats: Formats = Formats(Locale.getDefault())

    private fun formats(): Formats {
        val locale = Locale.getDefault()
        val current = formats
        if (current.locale == locale) return current
        return Formats(locale).also { formats = it }
    }

    /** "10:42" today, "Yesterday", "17 Sep" beyond that — the convention every phone uses. */
    fun listTimestamp(millis: Long, now: Long = System.currentTimeMillis()): String {
        if (millis <= 0L) return ""
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val formats = formats()
        return when {
            sameDay(then, today) -> formats.time.format(Date(millis))
            sameDay(then, yesterday) -> "Yesterday"
            else -> formats.day.format(Date(millis))
        }
    }

    fun fullTimestamp(millis: Long): String =
        if (millis <= 0L) "" else formats().full.format(Date(millis))

    fun duration(seconds: Long): String {
        if (seconds <= 0L) return ""
        val minutes = seconds / 60
        val remaining = seconds % 60
        return if (minutes > 0) "${minutes}m ${remaining}s" else "${remaining}s"
    }

    fun elapsed(sinceMillis: Long, now: Long = System.currentTimeMillis()): String {
        if (sinceMillis <= 0L) return ""
        val total = ((now - sinceMillis) / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    /**
     * Groups an Indian number the way people read it, and leaves anything else alone
     * rather than guessing at a foreign grouping convention.
     */
    fun displayNumber(raw: String?): String {
        if (raw.isNullOrBlank()) return "Private number"
        val info = PhoneNumberNormalizer.normalize(raw)
        return when (info.kind) {
            NumberKind.INDIAN_SUBSCRIBER ->
                "+91 ${info.nationalDigits.take(5)} ${info.nationalDigits.drop(5)}"
            NumberKind.INDIAN_SERVICE_CODE -> info.nationalDigits
            NumberKind.INTERNATIONAL -> "+${info.normalized}"
            NumberKind.PRIVATE -> "Private number"
            NumberKind.MALFORMED -> raw
        }
    }

    /** The two-or-three letter monogram used on every avatar. */
    fun initials(name: String?, fallbackNumber: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isNotEmpty() && trimmed.any { it.isLetter() }) {
            val parts = trimmed.split(' ', '.', '-').filter { it.isNotBlank() }
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                else -> parts.first().take(2).uppercase()
            }
        }
        val digits = fallbackNumber?.filter { it.isDigit() }.orEmpty()
        return digits.take(3).ifEmpty { "?" }
    }

    fun simLabel(slotIndex: Int?, label: String?): String = when {
        slotIndex == null -> label ?: "Unknown SIM"
        label.isNullOrBlank() -> "SIM ${slotIndex + 1}"
        else -> "SIM ${slotIndex + 1} · $label"
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
