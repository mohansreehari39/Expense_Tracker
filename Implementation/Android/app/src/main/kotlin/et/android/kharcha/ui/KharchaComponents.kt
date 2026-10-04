package et.android.kharcha.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import et.android.kharcha.ui.theme.kharcha
import et.core.domain.BudgetEvaluation
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign

/*
 * The building blocks of the redesigned app (concept A): one card style,
 * a big-number budget hero, week chips, list rows with an icon tile, the
 * space-switcher pill and the avatar. Screens compose these rather than
 * styling Material components one by one.
 */

/** Digits line up in columns of amounts. */
fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

/** A grouped block on a screen: the one card style used everywhere. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, kharcha.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
                }
            }
            content()
        }
    }
}

/** Small tonal action, e.g. "Settle". */
@Composable
fun TonalPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape, color = kharcha.tonal) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** Animated progress track; the fill color carries the budget status. */
@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), animationSpec = tween(900), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(kharcha.line)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(CircleShape).background(color))
    }
}

private fun BudgetEvaluation.fraction(): Float =
    if (allocated.minorUnits <= 0) 1f else (spent.minorUnits.toFloat() / allocated.minorUnits.toFloat())

/** The big "₹21,550 of ₹60,000" figure with its bar. Shows how much is over when overspent. */
@Composable
fun BudgetHero(label: String, evaluation: BudgetEvaluation?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
        if (evaluation == null) {
            Text("No budget set", style = MaterialTheme.typography.titleMedium, color = kharcha.muted)
            Text("Add one in this space's settings (gear icon).", style = MaterialTheme.typography.bodySmall, color = kharcha.muted)
            return@Column
        }
        val currency = evaluation.allocated.currency
        val left = evaluation.allocated.minorUnits - evaluation.spent.minorUnits
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                formatMoney(kotlin.math.abs(left), currency),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp).tabular(),
                color = if (left < 0) kharcha.over else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (left < 0) "over ${formatMoney(evaluation.allocated.minorUnits, currency)}" else "of ${formatMoney(evaluation.allocated.minorUnits, currency)}",
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = kharcha.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        ProgressTrack(evaluation.fraction(), statusColor(evaluation.status))
        Text(
            "Spent ${formatMoney(evaluation.spent.minorUnits, currency)}",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = kharcha.muted,
        )
    }
}

/** One chip per calendar-day week (1–7, 8–14, …); [amounts] shows what's left in each. */
@Composable
fun WeekChips(ranges: List<String>, amounts: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ranges.forEachIndexed { index, range ->
            val on = index == selected
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                border = if (on) null else BorderStroke(1.dp, kharcha.line),
            ) {
                Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(range, style = MaterialTheme.typography.labelSmall.tabular(), color = if (on) MaterialTheme.colorScheme.onPrimary else kharcha.muted)
                    Text(
                        amounts.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold).tabular(),
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Rounded square holding an icon — category tiles, space tiles. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, background: Color = kharcha.tonal, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(size / 3)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size / 2))
    }
}

/** A row in a list: icon tile, title + subtitle, trailing amount, optional overflow menu. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = Color.Unspecified,
    tileBackground: Color = kharcha.tonal,
    tileTint: Color = MaterialTheme.colorScheme.primary,
    menu: List<Pair<String, () -> Unit>> = emptyList(),
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, background = tileBackground, tint = tileTint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = kharcha.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.titleSmall.tabular(), color = trailingColor)
        }
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = kharcha.muted)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
                    }
                }
            }
        }
    }
}

/** Icon for an expense category, matched on its (user-chosen) name. */
fun categoryIcon(name: String?): ImageVector {
    val n = name.orEmpty().lowercase()
    return when {
        listOf("grocer", "vegetable", "supermarket", "shopping").any { it in n } -> Icons.Outlined.ShoppingCart
        listOf("electric", "power", "utilit", "gas", "water").any { it in n } -> Icons.Outlined.Bolt
        listOf("internet", "wifi", "broadband", "phone", "mobile").any { it in n } -> Icons.Outlined.Wifi
        listOf("rent", "house", "maintenance").any { it in n } -> Icons.Outlined.Home
        listOf("eat", "food", "restaurant", "lunch", "dinner", "swiggy", "zomato").any { it in n } -> Icons.Outlined.Restaurant
        listOf("pet", "dog", "cat", "vet").any { it in n } -> Icons.Outlined.Pets
        listOf("fuel", "petrol", "travel", "cab", "transport", "car").any { it in n } -> Icons.Outlined.DirectionsCar
        listOf("doctor", "medic", "health", "pharma").any { it in n } -> Icons.Outlined.LocalHospital
        listOf("school", "fee", "tuition", "book").any { it in n } -> Icons.Outlined.School
        else -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
}

/** The user's initial in a warm circle — opens Me. */
@Composable
fun Avatar(name: String, onClick: () -> Unit, size: Dp = 36.dp) {
    Surface(onClick = onClick, shape = CircleShape, color = Color(0xFFF1C27D), modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.42f).sp),
                color = Color(0xFF4A2D07),
            )
        }
    }
}

/** Top of a household/activity screen: the space-switcher pill, settings gear, avatar. */
@Composable
fun SpaceTopBar(
    name: String,
    icon: ImageVector,
    myName: String,
    onSwitchSpace: () -> Unit,
    onSettings: () -> Unit,
    onOpenMe: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = onSwitchSpace, shape = CircleShape, color = kharcha.tonal, modifier = Modifier.weight(1f, fill = false)) {
            Row(
                Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
                }
                Text(name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Switch household or activity", modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings for $name", tint = kharcha.muted) }
            Avatar(myName, onClick = onOpenMe)
        }
    }
}

/** Top of Summary and Me: a large title with the avatar. */
@Composable
fun ScreenTitleBar(title: String, myName: String, onOpenMe: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (onOpenMe != null) Avatar(myName, onClick = onOpenMe)
    }
}

/** Uppercase section label used inside sheets and lists. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = kharcha.muted, modifier = modifier)
}

/** The add/edit expense surface: a bottom sheet led by a big amount, the form, and a full-width Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseSheet(
    title: String,
    amountText: String,
    onAmountChange: (String) -> Unit,
    currency: String,
    saveEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
            BigAmountField(amountText, onAmountChange, currency)
            content()
            Button(
                onClick = onSave,
                enabled = saveEnabled,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(top = 4.dp),
            ) {
                Text("Save expense", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

/** Large centered amount entry with the decimal keypad. */
@Composable
fun BigAmountField(value: String, onValueChange: (String) -> Unit, currency: String) {
    val symbol = formatMoney(0, currency).takeWhile { !it.isDigit() }.trim()
    val style = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center).tabular()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Amount ($symbol)", style = MaterialTheme.typography.labelMedium, color = kharcha.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (value.isEmpty()) Text("0", style = style, color = kharcha.muted.copy(alpha = 0.45f))
                    inner()
                }
            },
        )
    }
}

/** "Who's it for" / "Who chipped in" summary on the expense sheet; tap to edit the split. */
@Composable
fun SplitSummaryRow(label: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = kharcha.tonal, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
    }
}
