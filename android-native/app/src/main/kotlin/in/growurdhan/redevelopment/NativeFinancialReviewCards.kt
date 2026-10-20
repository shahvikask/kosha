package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.growurdhan.model.NativeTaxLotTrace
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dossier remains ephemeral and needs a separate owner click to reveal. */
@Composable
internal fun NativeEmergencyDossierCard(book: NativeBooksRepository.Snapshot) {
    var approved by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Family emergency sheet", style = MaterialTheme.typography.titleMedium)
        Text("PRIVACY WARNING: printing, screenshots or sharing plaintext " +
            "can expose household finances. Nominee notes are NOT registered " +
            "nominations. Never treat them as confirmation by a registrar.")
        if (!visible) {
            OutlinedButton(onClick = { approved = true }) {
                Text("I understand the privacy warning")
            }
            Button(enabled = approved, onClick = {
                visible = true
                approved = false
            }) { Text("Reveal private family sheet") }
        } else {
            val dossier = NativeEmergencyDossier.prepare(book, explicitlyApproved = true)
            OutlinedButton(onClick = { visible = false }) { Text("Hide immediately") }
            Text("GrowUrDhan private family sheet • " + dossier.preparedOn)
            dossier.members.forEach {
                Text(it.name + " (" + it.relation + ")" +
                    (it.maskedPan?.let { pan -> " • PAN " + pan } ?: ""))
            }
            Text("Custody accounts", style = MaterialTheme.typography.titleMedium)
            dossier.accounts.forEach {
                Text(it.custodian + " • " + it.accountType + " • " + it.owner +
                    " • " + (it.maskedReference ?: "Number not recorded"))
                Text("Recorded nominee: " + (it.nominee ?: "Not confirmed"))
            }
            Text("Holdings", style = MaterialTheme.typography.titleMedium)
            dossier.holdings.forEach {
                Text(it.name + " • " + it.category + " • " + it.owner +
                    " • " + it.custody)
                Text("Units " + NativeBooksRepository.formatMinor(it.unitsScaled, 8) +
                    " • value " + (it.valueMinor?.let { minor ->
                        "₹" + NativeBooksRepository.formatMinor(minor, 4)
                    } ?: "unverified"))
                it.valueWarning?.let { warning -> Text(warning) }
            }
            Text(dossier.unresolvedValues.toString() +
                " values require source review. Contact the bank, registrar " +
                "or depository directly for official nomination/transmission.")
            Text("No plaintext document has been created or saved.")
        }
    }
}

/** Only signed, FIFO traceable economic facts; tax rates remain unverified. */
@Composable
internal fun NativeTaxWorkpaperCard(email: String, books: NativeBooksRepository,
    holdings: List<NativeBooksRepository.Holding>) {
    var selected by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var assessmentYear by remember {
        mutableStateOf(NativeTaxLotTrace.assessmentYear(LocalDate.now()))
    }
    var result by remember { mutableStateOf<NativeTaxLotTrace.Workpaper?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Tax-lot source workpaper", style = MaterialTheme.typography.titleMedium)
        Text("Original acquisition/sale IDs and integer FIFO parcels only. " +
            "Not a tax estimate or filing advice; Finance Act and FMV review required.")
        OutlinedButton(enabled = !busy, onClick = { expanded = true }) {
            Text(holdings.firstOrNull { it.id == selected }?.name ?: "Choose a holding")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            holdings.forEach { h ->
                DropdownMenuItem(text = { Text(h.name + " • " + h.personName) },
                    onClick = { selected = h.id; result = null; expanded = false })
            }
        }
        OutlinedTextField(assessmentYear, {
            assessmentYear = it
            result = null
        }, label = { Text("Assessment year (AY25-26)") })
        Button(enabled = !busy && selected.isNotEmpty(), onClick = {
            busy = true
            error = ""
            result = null
            scope.launch {
                try {
                    result = withContext(Dispatchers.IO) {
                        books.taxWorkpaper(email, selected, assessmentYear)
                    }
                } catch (_: Exception) {
                    error = "Tax workpaper unavailable: review the year and FIFO records."
                } finally { busy = false }
            }
        }) { Text("Review recorded FIFO sale parcels") }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        result?.let { sheet ->
            if (sheet.items.isEmpty()) Text("No recorded sales for " + sheet.assessmentYear)
            sheet.items.forEach { parcel ->
                Text("Sale " + parcel.saleEventId.take(12) + " • purchase " +
                    parcel.purchaseEventId.take(12) + " • " + parcel.acquired +
                    " to " + parcel.sold)
                Text("Proceeds ₹" + NativeBooksRepository.formatMinor(parcel.proceedsMinor, 4) +
                    " • FIFO cost " + (parcel.costMinor?.let {
                        "₹" + NativeBooksRepository.formatMinor(it, 4)
                    } ?: "UNKNOWN"))
            }
            if (sheet.missingCost) Text("Unknown cost: no aggregate gain reported.")
            else Text("Recorded realized FIFO gain/loss: ₹" +
                NativeBooksRepository.formatMinor(sheet.fullyCostedGainMinor ?: 0L, 4))
            Text("Statutory classification, grandfathering, exemption, slab, " +
                "cess and tax liability have NOT been established.")
        }
    }
}

/** Official AMFI-only NAV retrieval, no portfolio/ISIN/owner identifiers sent. */
@Composable
internal fun NativeAmfiNavCard(email: String, books: NativeBooksRepository,
    onCommitted: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text("Official AMFI NAV (mutual funds)", style = MaterialTheme.typography.titleMedium)
    Text("Downloads the public AMFI catalog over HTTPS without transmitting " +
        "PAN, household holdings or credentials. NSE and BSE are not yet connected. " +
        "A dated official NAV never replaces an owner-entered manual price.")
    Button(enabled = !busy, onClick = {
        busy = true
        message = ""
        scope.launch {
            try {
                val receipt = withContext(Dispatchers.IO) {
                    books.refreshAmfiNav(email)
                }
                message = "AMFI NAV parsed: " + receipt.catalogRows +
                    " ISINs; matched " + receipt.matched +
                    " mutual fund holdings; refreshed " +
                    receipt.displayedPriceChanged + " displayed prices."
                onCommitted()
            } catch (_: Exception) {
                message = "Official AMFI feed or authenticated vault unavailable. " +
                    "Previously recorded prices remain unchanged."
            } finally { busy = false }
        }
    }) { Text("Refresh official mutual-fund NAV") }
    if (message.isNotEmpty()) Text(message)
}

/**
 * Real on-device encrypted consent only. Does not claim a Google account is
 * connected or imply that OAuth has happened.
 */
@Composable
internal fun NativeGmailOptInCard(email: String, books: NativeBooksRepository,
    family: List<NativeBooksRepository.Person>) {
    var flags by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(email) {
        flags = emptyMap()
        try {
            flags = withContext(Dispatchers.IO) { books.gmailConsentStates(email) }
        } catch (_: Exception) {
            message = "Encrypted Gmail preferences unavailable."
        }
    }
    Text("Gmail import consent (local only)", style = MaterialTheme.typography.titleMedium)
    Text("Google OAuth account chooser, API credentials and historical downloads are " +
        "NOT connected. Opting in saves consent only, not an access token.")
    family.filter { it.active }.forEach { member ->
        OutlinedButton(enabled = !busy, onClick = {
            busy = true
            message = ""
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        books.setMemberGmailOptIn(email, member.id,
                            flags[member.id] != true)
                    }
                    flags = withContext(Dispatchers.IO) {
                        books.gmailConsentStates(email)
                    }
                } catch (_: Exception) {
                    message = "Gmail consent update refused; reauthenticate vault."
                } finally { busy = false }
            }
        }) {
            Text(member.name + ": " +
                (if (flags[member.id] == true) "Revoke local opt-in"
                 else "Opt in to future Gmail document import"))
        }
    }
    if (message.isNotEmpty()) Text(message)
}
