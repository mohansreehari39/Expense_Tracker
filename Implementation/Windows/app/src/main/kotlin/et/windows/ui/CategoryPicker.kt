package et.windows.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import et.windows.server.CategoryDto

/** Searchable create-or-select category field — see [SearchCreatePicker]. */
@Composable
fun CategoryPicker(
    categories: List<CategoryDto>,
    selectedCategoryId: String?,
    onCategorySelected: (CategoryDto) -> Unit,
    onCreateCategory: suspend (String) -> CategoryDto,
    modifier: Modifier = Modifier,
) {
    SearchCreatePicker(
        items = categories,
        idOf = { it.id },
        nameOf = { it.name },
        selectedId = selectedCategoryId,
        label = "Category",
        onItemSelected = onCategorySelected,
        onCreateItem = onCreateCategory,
        modifier = modifier,
    )
}
