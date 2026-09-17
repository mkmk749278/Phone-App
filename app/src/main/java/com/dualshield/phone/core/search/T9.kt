package com.dualshield.phone.core.search

/**
 * The keypad letter mapping, and nothing else.
 *
 * ```
 * 2 ABC   3 DEF   4 GHI   5 JKL
 * 6 MNO   7 PQRS  8 TUV   9 WXYZ
 * ```
 *
 * `KISHORE` encodes to `5474673`, so typing `5474` finds Kishore.
 */
object T9 {

    /** Marks a gap between words in an encoded name, so word starts stay findable. */
    const val WORD_SEPARATOR = ' '

    /**
     * Encodes [text] to its keypad digits.
     *
     * Letters become their digit, digits stay as they are (someone may have a number in
     * their contact name), and everything else becomes a [WORD_SEPARATOR] so that "Anand
     * Kumar" keeps a boundary the search can anchor on.
     *
     * Only ASCII letters map. A name in another script encodes to separators, which simply
     * means it is not reachable by T9 — the plain text search still finds it.
     */
    fun encode(text: String): String = buildString(text.length) {
        for (ch in text) {
            append(digitFor(ch))
        }
    }

    /** The keypad digit for one character, or [WORD_SEPARATOR]. */
    fun digitFor(ch: Char): Char = when (ch.lowercaseChar()) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        in '0'..'9' -> ch
        else -> WORD_SEPARATOR
    }

    /**
     * The encoded form of each word in [text], in order.
     *
     * Word starts are what make T9 feel right: typing the initials of a two-part name should
     * find it, and so should typing the surname on its own.
     */
    fun encodeWords(text: String): List<String> =
        encode(text).split(WORD_SEPARATOR).filter { it.isNotEmpty() }
}
