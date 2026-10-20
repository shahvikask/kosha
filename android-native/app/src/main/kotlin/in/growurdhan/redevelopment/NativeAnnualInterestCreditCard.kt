package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativeAnnualInterestCredit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Opt-in, manual, single-FY P22 subset. No background accruing or hardcoded rate. */
@Composable
internal fun NativeAnnualInterestCreditCard(
    email: String, repository: NativeAnnualInterestCreditRepository,
    holdings: List<NativeBooksRepository.Holding>, busyParent: Boolean,
    onCommitted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var holdingId by remember { mutableStateOf("") }
    var scheme by remember { mutableStateOf("PPF") }
    var principal by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var acquired by remember { mutableStateOf("") }
    var fyEnd by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var review by remember { mutableStateOf<NativeAnnualInterestCreditRepository.Review?>(null) }
    LaunchedEffect(holdingId, scheme, principal, rate, acquired, fyEnd) {
        review = null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Annual compound credit — limited reviewed support",
            style = MaterialTheme.typography.titleMedium)
        Text("Full financial year only. PPF/EPF/SSY with one deposit-unit, " +
            "no later contributions and a documented annual rate. Not a passbook, " +
            "official interest-rate history, partial-year estimate, or tax calculation.")
        holdings.filter { it.category == "FIXED_INCOME" }.forEach { h ->
            OutlinedButton(enabled = !working && !busyParent,
                onClick = { holdingId = h.id }) {
                Text(if (holdingId == h.id) "✓ ${h.name} — ${h.personName}"
                    else "${h.name} — ${h.personName}")
            }
        }
        OutlinedTextField(scheme, { scheme = it.trim().uppercase() },
            label = { Text("Scheme: PPF, EPF or SSY") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(principal, { principal = it },
            label = { Text("Current owner-confirmed corpus ₹") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(rate, { rate = it },
            label = { Text("Annual rate in basis points (710 = 7.10%)") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(acquired, { acquired = it },
            label = { Text("Original acquisition YYYY-MM-DD") },
            modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fyEnd, { fyEnd = it },
            label = { Text("Financial year end YYYY-03-31") },
            modifier = Modifier.fillMaxWidth())
        Button(enabled = !working && !busyParent && holdingId.isNotBlank(),
            onClick = {
                review = null
                message = ""
                val input = runCatching {
                    NativeAnnualInterestCredit.Input(scheme,
                        LocalDate.parse(fyEnd),
                        NativeBooksRepository.parseMinor(principal, 4),
                        requireNotNull(rate.toIntOrNull()), LocalDate.parse(acquired))
                }.getOrNull()
                if (input == null) {
                    message = "Enter exact principal, rate and real dates."
                } else {
                    working = true
                    scope.launch {
                        try {
                            review = withContext(Dispatchers.IO) {
                                repository.inspect(email, holdingId, input)
                            }
                        } catch (_: Exception) {
                            message = "Annual interest review refused: price, owner, rate, " +
                                "deposit history or financial-year evidence is insufficient."
                        } finally { working = false }
                    }
                }
            }) { Text("Preview annual credit") }
        review?.let { candidate ->
            Text("${candidate.credit.date}: ₹${NativeBooksRepository.formatMinor(
                candidate.credit.amountMinor, 4)} compound interest; proposed new " +
                "corpus ₹${NativeBooksRepository.formatMinor(
                    candidate.credit.closingMinor, 4)}")
            Text("Confirm this FY rate and balance against your passbook. " +
                "The app cannot independently verify issuer interest.")
            Button(enabled = !working && !busyParent, onClick = {
                working = true
                scope.launch {
                    try {
                        val receipt = withContext(Dispatchers.IO) {
                            repository.approve(email, candidate, true)
                        }
                        message = "One reviewed interest credit recorded; new corpus ₹" +
                            NativeBooksRepository.formatMinor(receipt.newBalanceMinor, 4)
                        review = null
                        onCommitted()
                    } catch (_: Exception) {
                        message = "No annual credit posted: this review is stale, " +
                            "duplicated or unsupported."
                    } finally { working = false }
                }
            }) { Text("Approve and record this FY interest") }
        }
        if (message.isNotBlank()) Text(message)
    }
}
