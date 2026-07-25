package et.android.kharcha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import et.android.kharcha.data.local.CategoryEntity

/** Searchable create-or-select category field — see [SearchCreatePicker]. */
@Composable
fun CategoryPicker(
    categories: List<CategoryEntity>,
    selectedCategoryId: String?,
    onCategorySelected: (CategoryEntity) -> Unit,
    onCreateCategory: suspend (String) -> CategoryEntity,
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
