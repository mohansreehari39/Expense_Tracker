package et.windows.ui

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
import et.windows.server.CategoryDto
import kotlinx.coroutines.launch

/**
 * A searchable, create-or-select category field — replaces a fixed row of
 * chips, since the household's category list is meant to grow and the
 * fixed-chip version had no way to add one. Typing filters the existing
 * list; picking a suggestion selects it. Typing a name with no
 * case-insensitive match offers "+ Create" — actual dedup safety is on
 * the server (`AddCategory` reuses an existing category if the trimmed,
 * case-insensitive name already matches, so e.g. "Eating out" reuses
 * "Eating Out" instead of creating a near-duplicate), this is just the
 * UI's best-effort filter to make picking the existing one the easy path.
 */
@Composable
fun CategoryPicker(
    categories: List<CategoryDto>,
    selectedCategoryId: String?,
    onCategorySelected: (CategoryDto) -> Unit,
    onCreateCategory: suspend (String) -> CategoryDto,
    modifier: Modifier = Modifier,
) {
    var query by remember(selectedCategoryId) {
        mutableStateOf(categories.find { it.id == selectedCategoryId }?.name ?: "")
    }
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val trimmedQuery = query.trim()
    val filtered = remember(categories, query) {
        if (query.isBlank()) categories else categories.filter { it.name.contains(query, ignoreCase = true) }
    }
    val hasExactMatch = categories.any { it.name.equals(trimmedQuery, ignoreCase = true) }

    Box(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Category") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !creating,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            filtered.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category)
                        query = category.name
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
                            val created = onCreateCategory(trimmedQuery)
                            onCategorySelected(created)
                            query = created.name
                            creating = false
                        }
                    },
                )
            }
        }
    }
}
