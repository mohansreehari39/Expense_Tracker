package et.android.kharcha.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.ProfileEntity

/**
 * Read-only view of this device's profile (see [SignupScreen] for where
 * it's first set) — editing is deliberately out of scope here, tracked
 * separately for V2, but not being able to even see what you signed up
 * with (particularly phone/email, now load-bearing for rejoin identity —
 * see et.core.domain.AddMember) was a real gap.
 */
@Composable
fun ProfileDialog(profile: ProfileEntity, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My Profile") },
        text = {
            Column {
                ProfileField("Name", profile.name)
                ProfileField("Phone", profile.phone ?: "—")
                ProfileField("Email", profile.email ?: "—")
                ProfileField("Age", profile.age?.toString() ?: "—")
                ProfileField("Gender", profile.gender ?: "—")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ProfileField(label: String, value: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
