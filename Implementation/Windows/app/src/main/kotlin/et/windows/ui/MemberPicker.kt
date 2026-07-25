package et.windows.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import et.windows.server.MemberDto

/** Searchable create-or-select "who paid" field — see [SearchCreatePicker]. */
@Composable
fun MemberPicker(
    members: List<MemberDto>,
    selectedMemberId: String?,
    onMemberSelected: (MemberDto) -> Unit,
    onCreateMember: suspend (String) -> MemberDto,
    modifier: Modifier = Modifier,
) {
    SearchCreatePicker(
        items = members,
        idOf = { it.id },
        nameOf = { it.displayName },
        selectedId = selectedMemberId,
        label = "Paid by",
        onItemSelected = onMemberSelected,
        onCreateItem = onCreateMember,
        modifier = modifier,
    )
}
