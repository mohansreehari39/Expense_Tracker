package et.windows.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import et.windows.server.SubcategoryDto

/** Searchable create-or-select subcategory field — see [SearchCreatePicker]. Optional, so [subcategories] may be empty until a category is chosen. */
@Composable
fun SubcategoryPicker(
    subcategories: List<SubcategoryDto>,
    selectedSubcategoryId: String?,
    onSubcategorySelected: (SubcategoryDto) -> Unit,
    onCreateSubcategory: suspend (String) -> SubcategoryDto,
    modifier: Modifier = Modifier,
) {
    SearchCreatePicker(
        items = subcategories,
        idOf = { it.id },
        nameOf = { it.name },
        selectedId = selectedSubcategoryId,
        label = "Subcategory (optional)",
        onItemSelected = onSubcategorySelected,
        onCreateItem = onCreateSubcategory,
        modifier = modifier,
    )
}
