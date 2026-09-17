package com.dualshield.phone.ui.shield

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.ui.components.AppListRow
import com.dualshield.phone.ui.components.VerticalSpacer
import com.dualshield.phone.ui.theme.Spacing

/** What the user picked from the add sheet. */
enum class AddRuleKind { NUMBER, PREFIX, CONTACT }

/**
 * "Add to list", modelled on the sheet MIUI's blocklist uses.
 *
 * Three concrete choices instead of one abstract "add rule" form: most people know whether
 * they want to block a number they can read out, a whole range, or someone in their contacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    onPick: (AddRuleKind) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.listItemGap),
        ) {
            Text(
                text = "Add to list",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            AppListRow(
                title = "Add phone number",
                subtitle = "Block one exact number",
                leading = { Icon(Icons.Filled.Dialpad, contentDescription = null) },
                onClick = { onPick(AddRuleKind.NUMBER) },
            )
            AppListRow(
                title = "Add prefix",
                subtitle = "Block every number that starts with it, like 140",
                leading = { Icon(Icons.Filled.Tag, contentDescription = null) },
                onClick = { onPick(AddRuleKind.PREFIX) },
            )
            AppListRow(
                title = "Add from contacts",
                subtitle = "Pick someone saved on this phone",
                leading = { Icon(Icons.Filled.People, contentDescription = null) },
                onClick = { onPick(AddRuleKind.CONTACT) },
            )
            VerticalSpacer(Spacing.sm)
        }
    }
}

/** The pattern type each choice maps to. */
fun AddRuleKind.patternType(): PatternType = when (this) {
    AddRuleKind.NUMBER, AddRuleKind.CONTACT -> PatternType.EXACT
    AddRuleKind.PREFIX -> PatternType.PREFIX
}
