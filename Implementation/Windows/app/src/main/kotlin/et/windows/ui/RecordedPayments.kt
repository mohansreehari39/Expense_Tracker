package et.windows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.windows.server.PaymentDto

/**
 * Payments recorded in a household or activity, newest first, each with
 * Undo (Test/Sync/corner-cases.md, S23). The Windows app may undo any
 * payment; a payment that may be the same debt recorded twice is flagged.
 */
@Composable
fun RecordedPayments(payments: List<PaymentDto>, nameOf: (String) -> String, onUndo: (PaymentDto) -> Unit) {
    if (payments.isEmpty()) return
    var toUndo by remember { mutableStateOf<PaymentDto?>(null) }

    Spacer(Modifier.height(16.dp))
    Text("Recorded Payments", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            payments.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${nameOf(p.fromId)} paid ${nameOf(p.toId)} ${formatMoney(p.amount)}")
                        Text(formatExpenseDate(p.settledAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (p.possibleDuplicate) {
                            Text(
                                "May have been recorded twice — undo one if so",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(onClick = { toUndo = p }) { Text("Undo") }
                }
            }
        }
    }

    toUndo?.let { p ->
        AlertDialog(
            onDismissRequest = { toUndo = null },
            title = { Text("Undo payment?") },
            text = { Text("${nameOf(p.fromId)} paid ${nameOf(p.toId)} ${formatMoney(p.amount)}. Undoing it puts the amount back on the balances, on every device.") },
            confirmButton = {
                Button(onClick = {
                    toUndo = null
                    onUndo(p)
                }) { Text("Undo") }
            },
            dismissButton = { TextButton(onClick = { toUndo = null }) { Text("Cancel") } },
        )
    }
}

/** The S24 label on an expense whose money changed after people settled up. */
@Composable
fun ChangedAfterSettlingLabel() {
    Text("Changed after settling", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
}
