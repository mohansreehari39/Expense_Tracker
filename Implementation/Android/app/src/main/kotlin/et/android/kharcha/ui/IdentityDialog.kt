package et.android.kharcha.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * There's no login — the first time a household/activity is opened on
 * this device, this asks which existing member/participant is "you" (or
 * lets you add yourself as a new one), so the app can compute owed/owe and
 * default who paid on a new expense. See [et.android.kharcha.data.ConnectionStore].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityDialog(
    title: String,
    people: List<Pair<String, String>>,
    onSelect: (id: String) -> Unit,
    onCreateNew: suspend (name: String) -> Pair<String, String>,
    onDismiss: () -> Unit,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var newName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (people.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(people) { (id, name) ->
                            FilterChip(selected = selectedId == id, onClick = { selectedId = id }, label = { Text(name) })
                        }
                    }
                }
                Text(
                    if (people.isEmpty()) "Add yourself:" else "Not listed? Add yourself:",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; selectedId = null },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedId != null || newName.isNotBlank(),
                onClick = {
                    val id = selectedId
                    if (id != null) {
                        onSelect(id)
                    } else {
                        scope.launch {
                            val (newId, _) = onCreateNew(newName.trim())
                            onSelect(newId)
                        }
                    }
                },
            ) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
