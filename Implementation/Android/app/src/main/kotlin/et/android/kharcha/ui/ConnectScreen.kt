package et.android.kharcha.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import et.android.kharcha.BuildConfig
import et.android.kharcha.data.decodeJoinInvite
import et.android.kharcha.data.discoverKharchaServer
import kotlinx.coroutines.launch

/**
 * Shown when no server is known yet. Scanning a household/activity's
 * Settings "+ Add" QR (see Windows' JoinInvite.kt) is the only way to
 * connect in a release build — it identifies which household/activity to
 * jump to, and this screen finds the server's *current* LAN address via
 * mDNS (`discoverKharchaServer`) rather than trusting a possibly-stale IP,
 * so it keeps working even after the Windows machine's IP changes. The
 * QR's own embedded address is used only as a fallback if discovery times
 * out. Manual IP:port entry is a debug-build-only convenience for
 * developing without relying on multicast working in an emulator.
 */
@Composable
fun ConnectScreen(onConnected: (baseUrl: String, deepLinkKind: String?, deepLinkId: String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualAddress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val invite = decodeJoinInvite(text)
        if (invite == null) {
            error = "That QR code isn't a Kharcha join code."
            return@rememberLauncherForActivityResult
        }
        error = null
        searching = true
        scope.launch {
            val discovered = discoverKharchaServer(context)
            searching = false
            val baseUrl = discovered?.baseUrl ?: invite.serverBaseUrl
            onConnected(baseUrl, invite.kind, invite.id)
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
                "Scan the join code shown in the Windows app's household or activity settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )

            if (searching) {
                CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                Text(
                    "Looking for your Kharcha server on this network…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Button(
                    onClick = { scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Scan QR code") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

            if (BuildConfig.DEBUG) {
                HorizontalDivider(Modifier.padding(vertical = 24.dp))
                Text("Debug only: enter the Windows machine's address", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = { manualAddress = it },
                    label = { Text("e.g. 192.168.1.20:47321") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        val address = manualAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                        if (address.isNotBlank()) onConnected("http://$address", null, null)
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) { Text("Connect") }
            }
        }
    }
}
