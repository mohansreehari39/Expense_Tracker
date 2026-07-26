package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.APP_VERSION
import et.windows.update.UpdateChecker
import et.windows.update.UpdateInfo
import et.windows.update.UpdateInstaller
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

private sealed interface CheckState {
    data object Checking : CheckState
    data object UpToDate : CheckState
    data class Available(val info: UpdateInfo) : CheckState
    data class Installing(val info: UpdateInfo) : CheckState
    data class Failed(val message: String) : CheckState
}

/** Triggered from the sidebar's "Check for Updates" row — checks GitHub Releases directly (see UpdateChecker), offers to download+launch the new installer, then exits so it isn't fighting a running instance of itself. */
@Composable
fun UpdateCheckDialog(onDismiss: () -> Unit) {
    var state by remember { mutableStateOf<CheckState>(CheckState.Checking) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val update = UpdateChecker.checkForUpdate()
        state = if (update != null) CheckState.Available(update) else CheckState.UpToDate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check for Updates") },
        text = {
            when (val current = state) {
                is CheckState.Checking -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Checking for a newer version…")
                }
                is CheckState.UpToDate -> Text("You're on the latest version ($APP_VERSION).")
                is CheckState.Available -> Column {
                    Text("Version ${current.info.version} is available (you have $APP_VERSION).")
                }
                is CheckState.Installing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("Downloading version ${current.info.version}…")
                }
                is CheckState.Failed -> Text(current.message, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            when (val current = state) {
                is CheckState.Available -> Button(onClick = {
                    state = CheckState.Installing(current.info)
                    scope.launch {
                        val result = UpdateInstaller.downloadAndLaunch(current.info)
                        result.onSuccess { exitProcess(0) }
                        result.onFailure { state = CheckState.Failed("Couldn't download the update — check your connection and try again.") }
                    }
                }) { Text("Download & Install") }
                else -> TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (state is CheckState.Available) TextButton(onClick = onDismiss) { Text("Not Now") }
        },
    )
}
