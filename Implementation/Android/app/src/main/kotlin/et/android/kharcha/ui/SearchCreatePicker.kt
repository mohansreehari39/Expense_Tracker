package et.android.kharcha.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch

/**
 * A searchable, create-or-select field: typing filters the existing items;
 * picking a suggestion selects it; typing a name with no case-insensitive
 * match offers "+ Create". Mirrors the Windows app's
 * `Implementation/Windows/.../ui/SearchCreatePicker.kt` — same UX, kept in
 * sync by hand. Actual dedup safety is in `LocalRepository.addCategory`,
 * this is just the UI's best-effort filter to make picking the existing
 * one the easy path.
 */
@Composable
fun <T> SearchCreatePicker(
    items: List<T>,
    idOf: (T) -> String,
    nameOf: (T) -> String,
    selectedId: String?,
    label: String,
    onItemSelected: (T) -> Unit,
    onCreateItem: suspend (String) -> T,
    modifier: Modifier = Modifier,
) {
    var query by remember(selectedId) {
        mutableStateOf(items.find { idOf(it) == selectedId }?.let(nameOf) ?: "")
    }
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val trimmedQuery = query.trim()
    val filtered = remember(items, query) {
        if (query.isBlank()) items else items.filter { nameOf(it).contains(query, ignoreCase = true) }
    }
    val hasExactMatch = items.any { nameOf(it).equals(trimmedQuery, ignoreCase = true) }

    Box(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !creating,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Non-focusable so it doesn't steal keyboard focus from the
            // text field on open — every keystroke after the first would
            // otherwise go nowhere until tapping the field again.
            properties = PopupProperties(focusable = false),
        ) {
            filtered.forEach { item ->
                DropdownMenuItem(
                    text = { Text(nameOf(item)) },
                    onClick = {
                        onItemSelected(item)
                        query = nameOf(item)
                        expanded = false
                    },
                )
            }
            if (trimmedQuery.isNotEmpty() && !hasExactMatch) {
                DropdownMenuItem(
                    text = { Text("+ Create \"$trimmedQuery\"", color = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        expanded = false
                        creating = true
                        scope.launch {
                            val created = onCreateItem(trimmedQuery)
                            onItemSelected(created)
                            query = nameOf(created)
                            creating = false
                        }
                    },
                )
            }
        }
    }
}
