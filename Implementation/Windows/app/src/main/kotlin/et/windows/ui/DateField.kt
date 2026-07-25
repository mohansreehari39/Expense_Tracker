package et.windows.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun formatExpenseDate(occurredAtMillis: Long): String =
    Instant.ofEpochMilli(occurredAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd MMM"))

/**
 * A click-to-pick date field for "when was this paid" — text entry is
 * error-prone for dates, so this only ever accepts a value chosen from the
 * calendar grid. [occurredAtMillis]/[onDateSelected] are local-midnight
 * epoch millis for the selected day (see the UTC-day conversion below),
 * matching how the server buckets expenses into weeks/months.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, occurredAtMillis: Long, onDateSelected: (Long) -> Unit, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    val formatted = remember(occurredAtMillis) {
        Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }

    OutlinedTextField(
        value = formatted,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = { IconButton(onClick = { showPicker = true }) { Text("📅") } },
        modifier = modifier,
    )

    if (showPicker) {
        // DatePicker represents the selected day as UTC-midnight millis,
        // regardless of system timezone — so the initial selection must be
        // built from the local calendar day, not the raw instant, or the
        // dialog can open pre-selected on the wrong day near midnight.
        val localDate = Instant.ofEpochMilli(occurredAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utcMillis ->
                        val pickedDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(pickedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}
