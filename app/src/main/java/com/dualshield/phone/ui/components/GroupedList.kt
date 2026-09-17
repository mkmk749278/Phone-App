package com.dualshield.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card-looking rows that are still real lazy-list items.
 *
 * The first version of every list screen put the whole list inside a single `item { }` so it
 * could sit in one Card. That composes every row up front — 300 contacts meant 300 rows built
 * before the first frame, which is what made scrolling stick. These helpers keep the
 * grouped-card look while letting Compose compose and recycle one row at a time.
 */
private val GroupRadius = 18.dp

/** Rounds only the corners at the ends of a group, so the rows read as one card. */
fun Modifier.groupedItemShape(index: Int, count: Int): Modifier {
    val top = if (index == 0) GroupRadius else 0.dp
    val bottom = if (index == count - 1) GroupRadius else 0.dp
    return clip(
        RoundedCornerShape(
            topStart = top,
            topEnd = top,
            bottomStart = bottom,
            bottomEnd = bottom,
        ),
    )
}

/**
 * Emits [items] as individual lazy items that together look like one card.
 *
 * [key] must be stable and unique — it is what lets Compose reuse rows instead of rebuilding
 * them, and what stops a list that shrinks mid-scroll from throwing.
 */
fun <T> LazyListScope.groupedItems(
    items: List<T>,
    key: (T) -> Any,
    dividerInset: Dp = 75.dp,
    content: @Composable (T) -> Unit,
) {
    val count = items.size
    itemsIndexed(
        items = items,
        key = { _, item -> key(item) },
        contentType = { _, _ -> "grouped-row" },
    ) { index, item ->
        Column(
            modifier = Modifier
                .groupedItemShape(index, count)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content(item)
            if (index < count - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = dividerInset),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}
