package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import et.windows.KharchaConfig
import javax.swing.JFileChooser

/**
 * Lets the user redirect where the SQLite database lives — see README's
 * V1 "Installer: app installs like a normal Windows app" item. Originally
 * scoped as an installer-time WiX dialog; implemented here as an in-app
 * setting instead (reachable any time, not just at install), since it
 * achieves the same outcome — the user chooses the path, defaulting to
 * `%LOCALAPPDATA%\Kharcha` — without needing custom WiX authoring that
 * can't be verified without a live, interactive install.
 */
@Composable
fun DataLocationDialog(onDismiss: () -> Unit) {
    var pathText by remember { mutableStateOf(KharchaConfig.dataDir().absolutePath) }
    var saved by remember { mutableStateOf(false) }
    val defaultPath = remember { KharchaConfig.defaultDataDir().absolutePath }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Data Location") },
        text = {
            Column {
                Text(
                    "Where Kharcha stores its database file. Defaults to " +
                        "%LOCALAPPDATA%\\Kharcha — change it if you'd rather keep " +
                        "your data somewhere else (an external drive, a synced " +
                        "folder, etc). Takes effect the next time you launch Kharcha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pathText,
                    onValueChange = { pathText = it; saved = false },
                    label = { Text("Folder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = {
                    val chooser = JFileChooser(pathText).apply {
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        dialogTitle = "Choose a folder for Kharcha's data"
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        pathText = chooser.selectedFile.absolutePath
                        saved = false
                    }
                }) { Text("Browse…") }
                if (pathText.trim() != defaultPath) {
                    TextButton(onClick = { pathText = defaultPath; saved = false }) { Text("Reset to default") }
                }
                if (saved) {
                    Text(
                        "Saved — restart Kharcha for this to take effect.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val trimmed = pathText.trim()
                KharchaConfig.setDataDirOverride(if (trimmed == defaultPath || trimmed.isEmpty()) null else java.io.File(trimmed))
                saved = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
