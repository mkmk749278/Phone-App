package com.dualshield.phone.ui.components

import com.dualshield.phone.core.model.NumberKind
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Formatting helpers shared by every screen, kept out of composables so they stay testable. */
object Formatting {

    // Formatters are rebuilt when the device locale changes. Caching them in a static
    // field would freeze whichever locale happened to be active when the class loaded.
    private var cachedLocale: Locale? = null
    private lateinit var timeFormat: SimpleDateFormat
    private lateinit var dayFormat: SimpleDateFormat
    private lateinit var fullFormat: SimpleDateFormat

    @Synchronized
    private fun formats(): Triple<SimpleDateFormat, SimpleDateFormat, SimpleDateFormat> {
        val locale = Locale.getDefault()
        if (cachedLocale != locale) {
            cachedLocale = locale
            timeFormat = SimpleDateFormat("h:mm a", locale)
            dayFormat = SimpleDateFormat("d MMM", locale)
            fullFormat = SimpleDateFormat("d MMM yyyy · h:mm a", locale)
        }
        return Triple(timeFormat, dayFormat, fullFormat)
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
        val (time, day, _) = formats()
        return when {
            sameDay(then, today) -> time.format(Date(millis))
            sameDay(then, yesterday) -> "Yesterday"
            else -> day.format(Date(millis))
        }
    }

    fun fullTimestamp(millis: Long): String =
        if (millis <= 0L) "" else formats().third.format(Date(millis))

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
