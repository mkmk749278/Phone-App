package com.dualshield.phone.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing scale, and the few control dimensions that must not drift.
 *
 * Before this existed the app used twelve different values between 2dp and 18dp — 17 for one
 * screen's gutter, 15 for another's row padding, 13 and 11 in single places — with no rule
 * behind any of them. Individually each looks fine. Together they are why moving between
 * screens felt like moving between prototypes: nothing lined up, and nothing was quite wrong
 * enough to point at.
 *
 * Everything here is a multiple of four, which is what the platform's own components are
 * built on, so app content and framework content sit on the same grid.
 *
 * Use the semantic names ([gutter], [rowPaddingH]) rather than the raw steps where one fits.
 * The step names exist for the cases that are genuinely just "a small gap", and a screen that
 * reaches for a raw number instead of either is usually about to reinvent a row.
 */
object Spacing {
    /** 4dp. Between two lines of the same thought — a title and its subtitle. */
    val xs = 4.dp

    /** 8dp. Between related controls in a row. */
    val sm = 8.dp

    /** 12dp. Inside a row, vertically. */
    val md = 12.dp

    /** 16dp. Between sections, and the standard screen gutter. */
    val lg = 16.dp

    /** 24dp. Around something that needs air: an empty state, a hero number. */
    val xl = 24.dp

    /** 32dp. Rarely. Above a primary action at the foot of a screen. */
    val xxl = 32.dp

    /** The left and right margin of every screen's content. */
    val gutter = lg

    /** Horizontal padding inside a list row, matching the gutter so text aligns down a column. */
    val rowPaddingH = lg

    /** Vertical padding inside a single-line list row. */
    val rowPaddingV = md

    /**
     * The gap between grouped rows in a card list.
     *
     * Deliberately tiny rather than zero: it reads as one grouped surface while still
     * separating the touch targets.
     */
    val listItemGap = 2.dp

    /**
     * Free space under the last item of a scrolling list.
     *
     * Enough to clear the bottom navigation bar and a floating action button together. A
     * list that ends exactly at its last row puts that row under the FAB, where it cannot
     * be read or tapped — and the one place a user notices is the oldest call in Recents.
     */
    val listBottomInset = 96.dp
}

/** Sizes that carry a rule rather than a preference. */
object Sizes {
    /**
     * The smallest thing worth asking a finger to hit.
     *
     * Android's accessibility guidance, and not negotiable for anything interactive. A
     * control smaller than this can still be given this much room with padding — the
     * visible shape and the touch target are allowed to differ, and usually should.
     */
    val minTouchTarget = 48.dp

    /** Avatar in a list row. */
    val avatarRow = 44.dp

    /** Avatar on a detail screen. */
    val avatarDetail = 92.dp

    /** Icon inside a list row's leading slot. */
    val rowIcon = 24.dp
}
