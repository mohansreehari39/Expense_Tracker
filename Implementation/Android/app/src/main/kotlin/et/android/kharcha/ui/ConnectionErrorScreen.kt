package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown instead of crashing when the server can't be reached at all (wrong
 * address, Windows app not running, wifi dropped) — a network exception
 * during the first load used to propagate uncaught out of a LaunchedEffect
 * coroutine and take the whole app down with it.
 */
@Composable
fun ConnectionErrorScreen(message: String, onRetry: () -> Unit, onChangeServer: (() -> Unit)? = null) {
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Couldn't reach the server", style = MaterialTheme.typography.headlineSmall)
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Retry") }
            if (onChangeServer != null) {
                OutlinedButton(onClick = onChangeServer, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Change server") }
            }
        }
    }
}
