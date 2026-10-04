package et.android.kharcha.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import et.android.kharcha.data.parseAmountMinorUnits
import et.core.domain.SettlementAmount
import et.core.model.Money

/**
 * Records how much [fromName] actually paid [toName] against a suggested
 * settlement. Opens pre-filled with the full [owedMinorUnits]; the user can
 * lower it for a partial payment (the rest stays owing). Validation is
 * Core's [SettlementAmount], shared with the Windows app.
 */
@Composable
fun SettleDialog(
    fromName: String,
    toName: String,
    owedMinorUnits: Long,
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (amountMinorUnits: Long) -> Unit,
) {
    var amountText by remember { mutableStateOf(Money.toPlainString(owedMinorUnits)) }
    val amount = parseAmountMinorUnits(amountText)
    val problem = SettlementAmount.problem(amount, owedMinorUnits)
    val hint = when (problem) {
        SettlementAmount.Problem.MISSING -> "Enter the amount paid"
        SettlementAmount.Problem.NOT_POSITIVE -> "Amount must be more than zero"
        SettlementAmount.Problem.MORE_THAN_OWED -> "Can't be more than the ${formatMoney(owedMinorUnits, currency)} owed"
        null -> if (amount == owedMinorUnits) {
            "Settles the full amount"
        } else {
            "Partial payment — ${formatMoney(owedMinorUnits - amount!!, currency)} will still be owed"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record payment") },
        text = {
            Column {
                Text("$fromName paid $toName", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Owed: ${formatMoney(owedMinorUnits, currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount paid ($currency)") },
                    singleLine = true,
                    isError = problem != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        confirmButton = {
            Button(enabled = problem == null, onClick = { onConfirm(amount!!) }) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
