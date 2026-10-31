package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import `in`.growurdhan.designsystem.GUDBrandLockup
import `in`.growurdhan.model.NativeAssetTaxonomy
import `in`.growurdhan.model.NativeInterestPayoutPlanner
import `in`.growurdhan.model.NativeOwnershipEconomicShare
import `in`.growurdhan.model.NativeTaxLotTrace
import `in`.growurdhan.model.NativeTradeCashReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
internal fun HoldingForm(people: List<NativeBooksRepository.Person>,
    custody: List<NativeBooksRepository.Custody>, busy: Boolean,
    save: (NativeBooksRepository.NewHolding) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("EQUITY") }
    var sub by remember { mutableStateOf("Other") }
    var quantity by remember { mutableStateOf("0") }
    var price by remember { mutableStateOf("0") }
    var knownCost by remember { mutableStateOf("Unknown") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var notes by remember { mutableStateOf("") }
    var isin by remember { mutableStateOf("") }
    var folioForStatements by remember { mutableStateOf("") }
    var openingPrice by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var accountMasked by remember { mutableStateOf("") }
    var maturity by remember { mutableStateOf("") }
    var couponBps by remember { mutableStateOf("") }
    var interestBps by remember { mutableStateOf("") }
    var emiMinor by remember { mutableStateOf("") }
    var emiState by remember { mutableStateOf("") }
    var emiCount by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var specialistValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var specialistError by remember { mutableStateOf("") }
    var reviewed by remember { mutableStateOf<NativeBooksRepository.NewHolding?>(null) }
    var reviewedSignature by remember { mutableStateOf<String?>(null) }
    Text("New holding — review fields before saving", style = MaterialTheme.typography.titleMedium)
    Choice("Owner", people.map { it.id to it.name }, owner, !busy) { owner = it; account = "" }
    Choice("Custody account", listOf("" to "No custody account") +
        custody.filter { it.personId == owner }.map { it.id to it.custodian },
        account, !busy) { account = it }
    Choice("Asset category", NativeAssetTaxonomy.subcategories.keys.map { it to it }, category,
        !busy) {
        category = it
        sub = NativeAssetTaxonomy.subcategories.getValue(it).first()
        specialistValues = NativeHoldingSpecialist.defaults(category, sub)
        specialistError = ""
    }
    Choice("Subcategory", NativeAssetTaxonomy.subcategories.getValue(category).map { it to it },
        sub, !busy) {
        sub = it
        specialistValues = NativeHoldingSpecialist.defaults(category, sub)
        specialistError = ""
    }
    OutlinedTextField(name, { name = it }, label = { Text("Instrument / holding name") },
        modifier = Modifier.fillMaxWidth().testTag("m27-holding-name"))
    OutlinedTextField(quantity, { quantity = it }, label = { Text("Opening units (may be 0)") },
        modifier = Modifier.fillMaxWidth().testTag("m27-holding-units"))
    OutlinedTextField(price, { price = it }, label = { Text("Current / last price per unit ₹ (valuation only)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(date, { date = it }, label = { Text("Acquisition date YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth().testTag("m27-holding-date"))
    Choice("Opening cost", listOf("Unknown" to "Unknown", "Known" to "Known"), knownCost,
        !busy) {
        knownCost = it
        if (it == "Unknown") openingPrice = ""
    }
    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(isin, { isin = it }, label = { Text("ISIN (optional, 12 characters)") },
        modifier = Modifier.fillMaxWidth())
    if (category == "MUTUAL_FUND") {
        OutlinedTextField(folioForStatements, { folioForStatements = it },
            label = { Text("Exact MF folio for statement matching (optional)") },
            modifier = Modifier.fillMaxWidth())
        Text("Enter only the folio shown on your statement. Never substitute a DP/client or masked reference.")
    }
    if (knownCost == "Known") {
        OutlinedTextField(openingPrice, { openingPrice = it },
            label = { Text("Verified acquisition cost per unit ₹ (required)") },
            modifier = Modifier.fillMaxWidth().testTag("m27-opening-cost"))
        Text("Enter a verified acquisition cost. Current market price is never used as cost.")
    } else {
        Text("Opening acquisition cost: Unknown. No purchase price or gain will be inferred.")
    }
    OutlinedTextField(institution, { institution = it }, label = { Text("Institution / issuer") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(accountMasked, { accountMasked = it },
        label = { Text("Masked account reference (if relevant)") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(maturity, { maturity = it }, label = { Text("Maturity YYYY-MM-DD") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(couponBps, { couponBps = it }, label = { Text("Coupon rate in basis points") },
        modifier = Modifier.fillMaxWidth())
    OutlinedTextField(interestBps, { interestBps = it },
        label = { Text("Interest rate in basis points") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(emiMinor, { emiMinor = it }, label = { Text("Monthly EMI ₹") },
        modifier = Modifier.fillMaxWidth())
    Choice("EMI status", listOf("" to "Not applicable") +
        listOf("PAYING", "PAUSED", "STOPPED").map { it to it }, emiState, !busy) {
        emiState = it
    }
    OutlinedTextField(emiCount, { emiCount = it },
        label = { Text("Remaining installments") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(location, { location = it }, label = { Text("Property/location") },
        modifier = Modifier.fillMaxWidth())
    SpecialistFields(category, sub, specialistValues) { key, value ->
        specialistValues = specialistValues + (key to value)
        specialistError = ""
    }
    if (specialistError.isNotEmpty())
        Text(specialistError, color = MaterialTheme.colorScheme.error)
    val incomeDraft = runCatching {
        val details = NativeHoldingSpecialist.merge(null, category, sub, specialistValues)
        NativeHoldingSpecialist.monthlyIncomeSchedule(
            details, date, LocalDate.now().toString())
    }.getOrNull()
    if (incomeDraft != null) {
        Text("Monthly income schedule (not booked): ₹" +
            NativeBooksRepository.formatMinor(incomeDraft.amountMinor, 4) +
            " per scheduled date; " + incomeDraft.scheduledThroughAsOf +
            " dates through today, ₹" +
            NativeBooksRepository.formatMinor(incomeDraft.scheduledTotalMinor, 4) +
            " projected only. Next: " + (incomeDraft.nextDate ?: "none"))
        Text("This preview never creates dividend, interest, cash or valuation events. " +
            "Record actual receipts only after owner review.")
    }
    // Any edited field invalidates an earlier review, including hidden category details.
    val currentSignature = listOf(owner, account, name, category, sub, quantity, price,
        knownCost, date, notes, isin, folioForStatements, openingPrice, institution,
        accountMasked, maturity, couponBps, interestBps, emiMinor, emiState, emiCount,
        location, specialistValues.toSortedMap().toString()).joinToString("\u001f")
    Button(enabled = !busy && owner.isNotBlank() && name.isNotBlank() &&
        (knownCost == "Unknown" || openingPrice.isNotBlank()),
        modifier = Modifier.testTag("m27-review-holding"), onClick = {
        val validDate = runCatching { LocalDate.parse(date) }.isSuccess
        val qty = runCatching { NativeBooksRepository.parseMinor(quantity, 8) }.getOrNull()
        val minor = runCatching { NativeBooksRepository.parseMinor(price, 4) }.getOrNull()
        val opened = if (knownCost == "Unknown") null else runCatching {
            NativeBooksRepository.parseMinor(openingPrice, 4)
        }.getOrNull()
        val emi = if (emiMinor.isBlank()) null else runCatching {
            NativeBooksRepository.parseMinor(emiMinor, 4)
        }.getOrNull()
        val coupon = couponBps.toIntOrNull()
        val interest = interestBps.toIntOrNull()
        val count = emiCount.toIntOrNull()
        val specialist = runCatching {
            NativeHoldingSpecialist.merge(null, category, sub, specialistValues)
        }
        specialistError = ""
        if (specialist.isFailure) specialistError = "Check the asset-specific fields before saving."
        if (!validDate) specialistError = "Enter a valid acquisition date (YYYY-MM-DD)."
        if (validDate && specialist.isSuccess && qty != null && minor != null &&
            (knownCost == "Unknown" || opened != null) &&
            (emiMinor.isBlank() || emi != null) &&
            (couponBps.isBlank() || coupon != null) &&
            (interestBps.isBlank() || interest != null) &&
            (emiCount.isBlank() || count != null)) {
            reviewed = NativeBooksRepository.NewHolding(owner, account.ifBlank { null }, name,
                category, sub, qty, minor, date, knownCost == "Known",
                isin = isin.ifBlank { null }, notes = notes.ifBlank { null },
                folioForStatements = if (category == "MUTUAL_FUND")
                    folioForStatements.ifBlank { null } else null,
                detailsJson = specialist.getOrNull(),
                openingUnitPriceMinor = opened,
                institutionName = institution.ifBlank { null },
                accountNumberMasked = accountMasked.ifBlank { null },
                maturityDate = maturity.ifBlank { null },
                couponRateBps = coupon, interestRateBps = interest,
                monthlyEmiMinor = emi, emiStatus = emiState.ifBlank { null },
                remainingEmiCount = count, location = location.ifBlank { null })
            reviewedSignature = currentSignature
        }
    }) { Text("Review holding details (no data saved)") }
    val approved = reviewed
    if (approved != null && reviewedSignature == currentSignature) {
        Text("Confirm holding before saving", style = MaterialTheme.typography.titleMedium)
        Text("Owner: " + (people.firstOrNull { it.id == approved.personId }?.name ?: "Unknown"))
        Text("Instrument: " + approved.name + " • " + approved.category + " / " + approved.subcategory)
        Text("Opening quantity: " + NativeBooksRepository.formatMinor(approved.quantityScaled, 8))
        Text("Current price (not acquisition cost): ₹" +
            NativeBooksRepository.formatMinor(approved.priceMinor, 4))
        Text(if (approved.knownOpeningCost) "Verified acquisition cost: ₹" +
            NativeBooksRepository.formatMinor(approved.openingUnitPriceMinor!!, 4) +
            " per unit" else "Acquisition cost: Unknown (no inferred basis)")
        Text("Custody: " + (custody.firstOrNull { it.id == approved.accountId }?.custodian
            ?: "No custody account"))
        Text("Acquisition date: " + approved.date)
        Text("Issuer / institution: " + (approved.institutionName ?: "Not specified"))
        Text("Location: " + (approved.location ?: "Not specified"))
        Text("Asset-specific details", style = MaterialTheme.typography.titleSmall)
        val reviewedFields = NativeHoldingSpecialist.fields(approved.category, approved.subcategory)
        val reviewedValues = NativeHoldingSpecialist.read(
            approved.detailsJson, approved.category, approved.subcategory)
        reviewedFields.forEach { field ->
            reviewedValues[field.key]?.takeIf { it.isNotBlank() }?.let { value ->
                Text(field.label + ": " + value)
            }
        }
        Text("Confirming will append an immutable opening event only for nonzero units.")
        Button(enabled = !busy, modifier = Modifier.testTag("m27-confirm-holding"),
            onClick = {
                // Preserve input for correction if asynchronous SQLCipher save is rejected.
                reviewed = null
                reviewedSignature = null
                save(approved)
            }) { Text("Confirm save holding") }
    }
}

