package cat.hudpro.opentracks.manager.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cat.hudpro.opentracks.R

/**
 * Shared save dialog asking name + folder (optional, with inline create) + activity type.
 * Used when a native recording finishes (forceChoice = true) and when importing a training.
 */
@Composable
fun TrackSaveDialog(
    title: String,
    statsLine: String?,
    defaultName: String,
    folders: List<String>,
    activityTypes: List<ActivityTypeOption>,
    initialTypeId: String? = null,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (name: String, folder: String, typeId: String?) -> Unit,
    onDismiss: () -> Unit,
    forceChoice: Boolean = false,
) {
    var name by remember { mutableStateOf(defaultName) }
    var folder by remember { mutableStateOf(ROOT) }
    var newFolder by remember { mutableStateOf("") }
    var typeId by remember { mutableStateOf(initialTypeId) }

    AlertDialog(
        onDismissRequest = { if (!forceChoice) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                statsLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.viewer_save_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(stringResource(R.string.save_type_label), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activityTypes.forEach { option ->
                        FilterChip(
                            selected = typeId == option.id,
                            onClick = { typeId = if (typeId == option.id) null else option.id },
                            label = { Text(option.label) },
                            leadingIcon = { Icon(option.icon, contentDescription = null) },
                        )
                    }
                }

                Text(stringResource(R.string.save_folder_label), style = MaterialTheme.typography.labelLarge)
                FolderChoice(stringResource(R.string.home_none_root), folder == ROOT && newFolder.isBlank()) {
                    folder = ROOT; newFolder = ""
                }
                folders.forEach { f ->
                    FolderChoice(f, folder == f && newFolder.isBlank()) { folder = f; newFolder = "" }
                }
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.home_new_folder_ellipsis)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chosen = newFolder.trim().ifBlank { folder }
                    onConfirm(name.trim().ifBlank { defaultName }, chosen, typeId)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
