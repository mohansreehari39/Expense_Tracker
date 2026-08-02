package et.android.kharcha.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import et.android.kharcha.data.local.SubcategoryEntity

/** Searchable create-or-select subcategory field — see [SearchCreatePicker]. Optional, so [subcategories] may be empty until a category is chosen. */
@Composable
fun SubcategoryPicker(
    subcategories: List<SubcategoryEntity>,
    selectedSubcategoryId: String?,
    onSubcategorySelected: (SubcategoryEntity) -> Unit,
    onCreateSubcategory: suspend (String) -> SubcategoryEntity,
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
