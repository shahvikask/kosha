package `in`.growurdhan.redevelopment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import `in`.growurdhan.model.NativeE09Analytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Scoped, dated, recorded-only financial coverage; deliberately not a tax return. */
@Composable
internal fun NativeRecordedFinanceCard(email: String, books: NativeBooksRepository,
    memberId: String, refreshKey: List<NativeBooksRepository.Holding>) {
    var report by remember(email, memberId) {
        mutableStateOf<NativeRecordedFinanceReport.Summary?>(null)
    }
    var loaded by remember(email, memberId) { mutableStateOf(false) }
    LaunchedEffect(email, memberId, refreshKey) {
        report = null
        loaded = false
        try {
            report = withContext(Dispatchers.IO) {
                books.recordedFinancialReport(email, memberId)
            }
        } catch (_: Exception) {
            // Locked/invalid ledger or closed custody cannot publish approximate totals.
        } finally { loaded = true }
    }
    fun money(value: java.math.BigInteger): String =
        "₹" + BigDecimal(value, 4).toPlainString()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Recorded financial coverage", style = MaterialTheme.typography.titleLarge)
        Text("Read-only book figures for this member view. Dated user/issuer-recorded " +
            "prices are not real-time quotes. These are incomplete asset subsets, " +
            "not verified household net worth, profit after tax, or a tax return.")
        val view = report
        if (!loaded) Text("Loading encrypted financial coverage…")
        else if (view == null) Text("Unavailable: unlock/review encrypted books. " +
            "No partial financial totals displayed.",
            modifier = Modifier.testTag("m29-finance-unavailable"))
        else {
            Text("Currently priced active assets: ${view.pricedAssetCount} holding(s), " +
                "${money(view.pricedAssetMinor)} (recorded dated values only).",
                modifier = Modifier.testTag("m29-finance-priced"))
            Text("Known current basis: ${view.countKnownBasis} of " +
                "${view.pricedAssetCount} priced holding(s), " +
                "${money(view.pricedKnownBasisMinor)}. " +
                "Known-basis unrealized change: ${money(view.knownUnrealizedMinor)}. " +
                "Missing basis is excluded, not zero.",
                modifier = Modifier.testTag("m29-finance-unrealized"))
            Text("Recorded realized gains/losses where sale cost is known: " +
                "${money(view.realizedKnownMinor)}. " +
                "Uncosted sale lots: ${view.realizedUnknownLots}. No tax rule inferred.",
                modifier = Modifier.testTag("m29-finance-realized"))
            Text("Excluded from current priced assets: " +
                "${view.unpricedActiveAssets} missing/stale/invalid quote, " +
                "${view.excludedReviewAssets} requiring reconciliation, " +
                "${view.excludedLiabilities} liability record(s); " +
                "${view.pricedUnknownBasisAssets} priced asset(s) have unknown cost; " +
                "${view.archivedExcluded} archived holding(s).",
                modifier = Modifier.testTag("m29-finance-exclusions"))
            if (view.pricedCategories.isEmpty()) {
                Text("No eligible dated asset values. No allocation inferred.",
                    modifier = Modifier.testTag("m29-finance-empty"))
            } else {
                Text("Recorded category totals (not hybrid look-through; category " +
                    "subsets sum only to the dated priced-asset subtotal).")
                for ((category, value) in view.pricedCategories) {
                    Text("$category: ${money(value)}",
                        modifier = Modifier.testTag("m29-finance-category-$category"))
                }
            }
            // E09 concentration is only within the already verified priced
            // subset for the currently selected SQLCipher owner lens.
            val exposure = NativeE09Analytics.allocation(
                view.pricedCategories, view.pricedAssetMinor, view.excludedLiabilities)
            if (exposure.categories.isEmpty()) {
                Text("Concentration N/A: no eligible dated priced asset values.",
                    modifier = Modifier.testTag("e09-concentration-empty"))
            } else {
                Text("Priced-asset concentration (10,000 bps = 100% of the " +
                    "incomplete priced subset, NOT household net worth):",
                    modifier = Modifier.testTag("e09-concentration-title"))
                for (part in exposure.categories) {
                    Text("${part.name}: ${part.basisPoints} / 10,000 bps " +
                        "(${money(part.pricedMinor)})",
                        modifier = Modifier.testTag("e09-category-${part.name}"))
                }
                Text("Largest recorded priced category: ${exposure.leadingCategory} " +
                    "(${exposure.leadingBasisPoints} bps)",
                    modifier = Modifier.testTag("e09-largest"))
            }
            Text("Liabilities: ${exposure.liabilityRecordCountExcluded} record(s) " +
                "excluded from assets; loan value is never netted into this subtotal.",
                modifier = Modifier.testTag("e09-liability-separated"))
            Text("Goal progress requires complete linked dated valuations; " +
                "contributions and expenses appear only if separately disclosed below. " +
                "Buys and sells never prove bank cash movements.",
                modifier = Modifier.testTag("e09-unverified-series"))
            for (row in NativeE09Analytics.recordedYears(view.incomeByCalendarYear,
                view.realizedByCalendarYear)) {
                Text("Calendar ${row.year} · recorded income ${money(row.recordedIncomeMinor)} " +
                    "· known-cost realized gain/loss ${money(row.realizedKnownMinor)} " +
                    "(no expense or tax inference)",
                    modifier = Modifier.testTag("e09-year-${row.year}"))
            }
            if (view.incomeByCalendarYear.isEmpty()) {
                Text("No recorded dividends or interest (not proof of zero income).",
                    modifier = Modifier.testTag("m29-finance-income-empty"))
            } else {
                Text("Recorded cash income by calendar year; no tax-year treatment inferred.")
                for ((year, value) in view.incomeByCalendarYear) {
                    Text("$year: ${money(value)}",
                        modifier = Modifier.testTag("m29-finance-income-$year"))
                }
            }
            for ((year, value) in view.realizedByCalendarYear) {
                Text("Recorded realized known-cost gain/loss in calendar year " +
                    "$year: ${money(value)}",
                    modifier = Modifier.testTag("m29-finance-gain-$year"))
            }
        }
    }
}
