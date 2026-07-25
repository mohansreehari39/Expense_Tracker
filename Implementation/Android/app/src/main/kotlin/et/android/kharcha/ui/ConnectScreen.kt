package et.android.kharcha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import et.android.kharcha.data.decodeJoinInvite

/**
 * Shown when no server is known yet. A household/activity's Settings "+
 * Add" QR (see Windows' JoinInvite.kt) carries the server's address, so
 * scanning one both connects and remembers which household/activity to
 * jump to next; manual IP:port entry is the fallback when there's nothing
 * to scan yet (e.g. connecting to browse before joining anything specific).
 */
@Composable
fun ConnectScreen(onConnected: (baseUrl: String, deepLinkKind: String?, deepLinkId: String?) -> Unit) {
    var manualAddress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val invite = decodeJoinInvite(text)
        if (invite == null) {
            error = "That QR code isn't a Kharcha join code."
        } else {
            onConnected(invite.serverBaseUrl, invite.kind, invite.id)
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Connect to Kharcha", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan a join code shown in the Windows app's household or activity settings, or enter its address manually.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            Button(
                onClick = { scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Scan QR code") }

            HorizontalDivider(Modifier.padding(vertical = 24.dp))

            Text("Or enter the Windows machine's address", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = manualAddress,
                onValueChange = { manualAddress = it },
                label = { Text("e.g. 192.168.1.20:47321") },
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(
                onClick = {
                    val address = manualAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                    if (address.isBlank()) {
                        error = "Enter an address first."
                    } else {
                        onConnected("http://$address", null, null)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Connect") }
        }
    }
}
