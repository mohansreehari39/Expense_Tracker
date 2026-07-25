package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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

/**
 * Shown once, before anything else — there's no login, so this name is
 * this device's permanent identity everywhere it's used (see
 * [et.android.kharcha.data.IdentityResolver]). Not tied to any server;
 * you can set this up before ever connecting to a Windows instance.
 */
@Composable
fun SignupScreen(onSignedUp: (name: String) -> Unit) {
    var name by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Welcome to Kharcha", style = MaterialTheme.typography.headlineSmall)
            Text(
                "What's your name? This is set once and used everywhere you're a member or participant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSignedUp(name.trim()) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Get Started") }
        }
    }
}
