package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val POLL_INTERVAL_MS = 1500L
private const val AUTO_CLOSE_DELAY_MS = 1200L

/**
 * Shown from "+ Add" in Household/Activity settings — QR-only, scanned by
 * the Kharcha Android app's "Join Household/Activity" flow (see
 * SyncEngine.joinHousehold/joinActivity). Polls [onPollForJoin] while open
 * so the user sees the moment someone actually joins, rather than closing
 * the dialog not knowing whether the scan worked. There's no manual
 * add-by-name fallback — joining is QR-only.
 */
@Composable
fun AddPersonDialog(title: String, qrPayload: String, onDismiss: () -> Unit, onPollForJoin: suspend () -> String?) {
    var joinedName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        while (isActive && joinedName == null) {
            delay(POLL_INTERVAL_MS)
            joinedName = onPollForJoin()
        }
    }

    // Briefly show the confirmation, then close on its own — no need to make
    // the user click "Done" once the person has already joined.
    LaunchedEffect(joinedName) {
        if (joinedName != null) {
            delay(AUTO_CLOSE_DELAY_MS)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                QrCodeImage(qrPayload, modifier = Modifier.size(180.dp))
                Spacer(Modifier.height(12.dp))
                val name = joinedName
                if (name == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Scan with the Kharcha Android app to join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Text(
                        "✓ $name joined",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Teal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}
