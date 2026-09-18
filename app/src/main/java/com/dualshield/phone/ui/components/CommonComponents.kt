package com.dualshield.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dualshield.phone.ui.theme.Sizes
import com.dualshield.phone.ui.theme.Spacing

/**
 * Minimum touch target everywhere, so nothing in the app is hard to hit one-handed.
 *
 * Kept as an alias of the token rather than a second declaration of the same number: this
 * file and [Sizes] each used to state it, which is how a 42dp control got past both.
 */
val MinTouchTarget = Sizes.minTouchTarget

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

/**
 * The one avatar in the app.
 *
 * Every surface that shows a person — Recents, the dialer, Call Details, Contacts, Messages,
 * the in-call screen — draws this, so a contact cannot have a photo on one screen and a
 * monogram on the next. When [photoUri] resolves, the real contact photo is shown; while it
 * loads, or when there is none, the monogram stands in rather than a blank circle.
 *
 * Decorative — the row's own text carries the accessible label.
 */
@Composable
fun ContactAvatar(
    name: String?,
    number: String?,
    modifier: Modifier = Modifier,
    photoUri: String? = null,
    size: Dp = Sizes.avatarRow,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // Initials when there is a name; a person glyph when there is not. The fallback
        // used to be the first three digits of the number, which is "919" for every Indian
        // mobile ever saved — a column of identical circles that says nothing about anyone.
        val initials = Formatting.initials(name, number)
        if (initials != null) {
            Text(
                text = initials,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(size * 0.55f),
            )
        }

        // The photo is drawn over the monogram rather than swapped in for it. Coil decodes
        // and caches off the main thread, and anything that does not resolve — no photo, a
        // thumbnail the provider will no longer serve, a contact deleted since the list was
        // built — simply leaves the monogram visible instead of a hole in the row.
        if (!photoUri.isNullOrBlank()) {
            AsyncImage(
                model = photoUri,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** The label above a group of rows. One definition, so headings line up across screens. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.sm),
    )
}

/** The app's one card style: quiet surface, hairline outline, generous radius. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.ui.unit.Dp = Spacing.xs,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.dp,
                    color = Color.Transparent,
                    shape = MaterialTheme.shapes.large,
                )
                .padding(vertical = contentPadding),
            content = content,
        )
    }
}

/**
 * The list row used by Recents, Messages, Contacts and the blocked-call list.
 *
 * One component for all four is deliberate: the product's promise is that Shield looks like
 * the rest of the phone, and shared rows are what make that true rather than aspirational.
 */
@Composable
fun AppListRow(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentDescription: String? = null,
    subtitleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Sizes.listRowMinHeight)
            .padding(horizontal = Spacing.rowPaddingH, vertical = Spacing.md)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // One line for a row whose subtitle is metadata, more where the
                    // subtitle is the content — a message preview cut at one line tells
                    // you a message arrived and nothing about what it says.
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun RowDivider(
    modifier: Modifier = Modifier,
    insetStart: androidx.compose.ui.unit.Dp = Sizes.rowDividerInset,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = insetStart),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Friendly, never alarming. No red screens for "nothing has happened yet". */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.let {
            Spacer(Modifier.height(Spacing.sm))
            it()
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                extraContent?.invoke()
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Two- or three-way selector used for Recents/Favorites and the Vault tabs. */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    )
                    .clickable { onSelect(index) }
                    .heightIn(min = MinTouchTarget)
                    .semantics { this.contentDescription = "$label${if (selected) ", selected" else ""}" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/**
 * A small state chip.
 *
 * When [onClick] is supplied the pill becomes a control and grows to the 48dp minimum touch
 * target, so flipping a rule's action is a real tap target rather than a 20dp sliver.
 */
@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    clickLabel: String? = null,
) {
    Box(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .heightIn(min = MinTouchTarget)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClick = onClick)
                        .padding(Spacing.xs)
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) Modifier else Modifier.clip(MaterialTheme.shapes.extraSmall),
            )
            .semantics { if (clickLabel != null) contentDescription = clickLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(containerColor)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

@Composable
fun VerticalSpacer(height: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun HorizontalSpacer(width: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.width(width))
}
