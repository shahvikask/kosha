package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativePpfSsyPassbookAccrual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Explicit, non-posting P11 FY comparison of owner-entered passbook figures.
 * Unlike the independently protected P22 annual-credit path this form can
 * NEVER save rates, manufacture deposit transactions or mutate SQLCipher.
 */
@Composable
internal fun NativePpfSsyPassbookCard(email: String,
    books: NativeBooksRepository,
    holdings: List<NativeBooksRepository.Holding>,
    busyParent: Boolean
) {
    val scope = rememberCoroutineScope()
    val eligible = holdings.filter { h ->
        h.category == "FIXED_INCOME" && !h.archived && !h.needsReview &&
            (h.subcategory.uppercase().contains("PPF") ||
                h.subcategory.uppercase().contains("PUBLIC PROVIDENT") ||
                h.subcategory.uppercase().contains("SSY") ||
                h.subcategory.uppercase().contains("SUKANYA"))
    }
    var holdingId by remember { mutableStateOf("") }
    var fyEnd by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var openingRef by remember { mutableStateOf("") }
    var closing by remember { mutableStateOf("") }
    var closingRef by remember { mutableStateOf("") }
    var rates by remember { mutableStateOf("") }
    var deposits by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var result by remember {
        mutableStateOf<NativePpfSsyPassbookAccrual.Series?>(null)
    }
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(holdingId, fyEnd, opening, openingRef, closing, closingRef,
        rates, deposits) {
        result = null
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("PPF / SSY dated passbook comparison — read only",
            style = MaterialTheme.typography.titleMedium)
        Text("Compare one completed April–March financial year. Enter the four " +
            "actual quarter-start rates, EVERY dated contribution, and opening/" +
            "closing values from your passbook. References are owner-entered, " +
            "not independently verified by the issuer. An undisclosed " +
            "withdrawal, adjustment or missing deposit makes the estimate invalid.")
        Text("Monthly calculation uses the balance on the 5th (including that " +
            "day's deposits), with interest summed in scaled minor units. " +
            "Issuer rounding or posting may differ. No automatic ledger posting.")
        eligible.forEach { h ->
            OutlinedButton(enabled = !working && !busyParent,
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { holdingId = h.id; message = "" }) {
                Text((if (h.id == holdingId) "Selected: " else "Choose: ") +
                    h.name + " • " + h.personName)
            }
        }
        val chosen = eligible.firstOrNull { it.id == holdingId }
        if (chosen != null) {
            Text("Owner: ${chosen.personName} • Scheme: ${chosen.subcategory}")
            OutlinedTextField(fyEnd, { fyEnd = it },
                label = { Text("Financial year end YYYY-03-31") },
                modifier = Modifier.fillMaxWidth().testTag("m27-passbook-fy"))
            OutlinedTextField(opening, { opening = it },
                label = { Text("1 April opening balance ₹") },
                modifier = Modifier.fillMaxWidth().testTag("m27-passbook-opening"))
            OutlinedTextField(openingRef, { openingRef = it },
                label = { Text("Opening passbook reference") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(closing, { closing = it },
                label = { Text("31 March closing passbook balance ₹") },
                modifier = Modifier.fillMaxWidth().testTag("m27-passbook-closing"))
            OutlinedTextField(closingRef, { closingRef = it },
                label = { Text("Closing passbook reference") },
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(rates, { rates = it },
                label = { Text("Four rates: YYYY-MM-DD,basis-points,source-ref " +
                    "(one row each: Apr/Jul/Oct/Jan)") },
                modifier = Modifier.fillMaxWidth().testTag("m27-passbook-rates"))
            OutlinedTextField(deposits, { deposits = it },
                label = { Text("Deposits: YYYY-MM-DD,amount-rupees,row-id,passbook-ref " +
                    "(one row each; blank only when none)") },
                modifier = Modifier.fillMaxWidth().testTag("m27-passbook-deposits"))
            Button(enabled = !working && !busyParent, onClick = {
                result = null
                message = ""
                val year = runCatching {
                    val kind = if (chosen.subcategory.uppercase().contains("PPF") ||
                        chosen.subcategory.uppercase().contains("PUBLIC PROVIDENT"))
                        NativePpfSsyPassbookAccrual.Scheme.PPF
                    else NativePpfSsyPassbookAccrual.Scheme.SSY
                    fun rows(raw: String, expected: Int): List<List<String>> =
                        raw.lines().filter { it.isNotBlank() }.map { line ->
                            line.split(",").map { it.trim() }.also {
                                require(it.size == expected && it.all(String::isNotBlank))
                            }
                        }
                    val q = rows(rates, 3).map { line ->
                        NativePpfSsyPassbookAccrual.QuarterRate(
                            LocalDate.parse(line[0]), line[1].toInt(), line[2])
                    }
                    val d = rows(deposits, 4).map { line ->
                        NativePpfSsyPassbookAccrual.Deposit(
                            chosen.personId, chosen.id, LocalDate.parse(line[0]),
                            NativeBooksRepository.parseMinor(line[1], 4),
                            line[2], line[3])
                    }
                    NativePpfSsyPassbookAccrual.YearEvidence(chosen.personId,
                        chosen.id, kind, LocalDate.parse(fyEnd),
                        NativeBooksRepository.parseMinor(opening, 4),
                        openingRef.trim(), d, q,
                        NativeBooksRepository.parseMinor(closing, 4),
                        closingRef.trim())
                }
                if (year.isFailure) message =
                    "Invalid evidence rows, dates or amounts. No comparison produced."
                else {
                    working = true
                    scope.launch {
                        try {
                            result = withContext(Dispatchers.IO) {
                                books.previewPpfSsyPassbook(email, chosen.id,
                                    listOf(year.getOrThrow()))
                            }
                        } catch (_: Exception) {
                            message = "Review refused: missing quarter/dated " +
                                "evidence, identity mismatch, locked vault or " +
                                "unreconciled holding. No data was written."
                        } finally { working = false }
                    }
                }
            }, modifier = Modifier.testTag("m27-passbook-compare")
                .heightIn(min = 48.dp)) { Text("Compare owner-entered passbook evidence") }
            result?.let { comparison ->
                val value = comparison.comparisons.single()
                Text("Calculated FY interest ₹" +
                    NativeBooksRepository.formatMinor(
                        value.calculatedInterestMinor, 4))
                Text("Calculated closing ₹" +
                    NativeBooksRepository.formatMinor(
                        value.calculatedClosingMinor, 4))
                Text(if (value.matchesEnteredClosing)
                    "Matches the owner-entered closing value only. " +
                        "Issuer/passbook authenticity and completeness NOT verified."
                else "MISMATCH: calculated closing differs from the owner-entered " +
                    "passbook closing. Do not post interest or treat this as reconciled.",
                    color = if (value.matchesEnteredClosing)
                        MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("m27-passbook-result"))
            }
        } else Text("Choose an active, reviewed PPF or SSY holding to compare.")
        if (message.isNotBlank()) Text(message,
            color = MaterialTheme.colorScheme.error)
    }
}
