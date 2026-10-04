package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.local.ProfileEntity
import et.android.kharcha.ui.theme.kharcha

/**
 * Me → Edit profile. Same fields and rules as signup ([ProfileForm]). Saving
 * keeps this device's identity (deviceId), so it's still the same person
 * everywhere. A new name shows in households/activities that live only on
 * this phone; ones on a server keep the name they were joined with until
 * the server supports renaming members.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    profile: ProfileEntity,
    onDismiss: () -> Unit,
    onSave: (name: String, age: Int?, gender: String?, phone: String, email: String) -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(ProfileDraft.from(profile)) }
    val changed = draft != ProfileDraft.from(profile)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Edit profile", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            ProfileForm(draft, onChange = { draft = it })
            if (draft.name.trim() != profile.name) {
                Text(
                    "Your new name shows in households and activities on this phone. Ones shared through a server keep the name you joined with for now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = kharcha.warn,
                )
            }
            Button(
                enabled = draft.isValid && changed,
                onClick = { onSave(draft.name.trim(), draft.age.toIntOrNull(), draft.gender, draft.phone.trim(), draft.email.trim()) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save profile", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
        }
    }
}
