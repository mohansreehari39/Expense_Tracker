package et.windows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * Device-level pairing — separate from joining any specific household or
 * activity (see JoinInvite.kt's "server" kind). Scanning this QR from the
 * Android app just registers "this phone talks to this Windows instance";
 * it doesn't create or link any data by itself. Households/activities are
 * joined afterward with their own QR from their own Settings dialog.
 *
 * Polls [ApiClient.devices] while open so the user sees the moment the
 * phone actually connects, rather than closing the dialog not knowing
 * whether the scan worked.
 */
@Composable
fun PairAndroidDeviceDialog(api: ApiClient, onDismiss: () -> Unit) {
    var connectedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val before = runCatching { api.devices() }.getOrDefault(emptyList())
        val beforeLastSeen = before.associate { it.id to it.lastSeenAt }
        while (isActive && connectedLabel == null) {
            delay(POLL_INTERVAL_MS)
            val current = runCatching { api.devices() }.getOrNull() ?: continue
            val newlySeen = current.find { (beforeLastSeen[it.id] ?: 0L) < it.lastSeenAt }
            if (newlySeen != null) connectedLabel = newlySeen.label
        }
    }

    // Briefly show the confirmation, then close on its own — no need to make
    // the user click "Done" once the connection is already established.
    LaunchedEffect(connectedLabel) {
        if (connectedLabel != null) {
            delay(AUTO_CLOSE_DELAY_MS)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Android Device") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                QrCodeImage(encodeJoinInvite(joinInviteForServerPairing()), modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(12.dp))
                val label = connectedLabel
                if (label == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "In the Kharcha Android app, choose \"Connect to Server\" and scan this code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Text(
                        "✓ Connected: $label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Teal,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
