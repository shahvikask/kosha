package `in`.growurdhan.domain.finance

import `in`.growurdhan.core.model.Holding
import `in`.growurdhan.core.model.LedgerEvent
import `in`.growurdhan.core.model.LedgerKind
import `in`.growurdhan.core.model.MoneyScaled
import `in`.growurdhan.domain.vault.VaultRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * v2 financial-book adapter. Pre-release v1 events retain their exact totals but may
 * lack unit price, charges and correction lineage; never manufacture missing evidence.
 */
fun LedgerEvent.toFinancialEvent(): FinancialEvent {
    if (kind == LedgerKind.CORRECTION && supersedesEventId == null) {
        throw IllegalStateException(
            "A pre-v2 correction is missing its supersession reference; cannot prove invested cost."
        )
    }
    val financialKind = if (kind == LedgerKind.ADJUSTMENT)
        FinancialEventKind.SNAPSHOT_ADJUSTMENT else FinancialEventKind.valueOf(kind.name)
    return FinancialEvent(
        id = id, holdingId = holdingId, ownerId = ownerId, kind = financialKind,
        date = LocalDate.parse(eventDate), quantityScaled = quantityDeltaScaled,
        unitPrice = unitPrice, totalAmount = amount,
        charges = charges, supersedesEventId = supersedesEventId,
    )
}

fun Holding.toFinancialHolding(): FinancialHolding = FinancialHolding(
    holdingId = id, ownerId = ownerId, name = name, category = assetCategory,
    unitPrice = currentUnitPrice, storedQuantityScaled = quantity.raw,
)

/** Only a read. Financial mutations always remain repository transactions. */
class CalculateFamilyPortfolio @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(asOf: LocalDate): PortfolioReport {
        val book = vault.readBook()
        return PortfolioEngine.derive(
            book.holdings.map { it.toFinancialHolding() },
            book.events.map { it.toFinancialEvent() },
            asOf,
            book.members.map { it.id },
        )
    }
}

/** Review-only use case. No financial events are written from unapproved statements. */
class PreviewStatementReconciliation @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(
        ownerId: `in`.growurdhan.core.model.OwnerId,
        sha256: String,
        lines: List<StatementLine>,
    ): StatementReview {
        val book = vault.readBook()
        require(book.members.any { it.id == ownerId }) { "Statement owner is not in the family vault." }
        return SnapshotReconciler.review(
            lines,
            book.holdings.map { it.toFinancialHolding() },
            book.events.map { it.toFinancialEvent() },
            ownerId,
            sha256,
        )
    }
}

/** User-authorized manual transaction batch, committed as one SQLite transaction. */
class AppendFinancialEvents @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(events: List<FinancialEvent>) {
        require(events.isNotEmpty()) { "No approved financial events." }
        vault.appendFinancialEvents(events)
    }
}

/**
 * The intended user-action path: source review and state-bound confirmation.
 * Callers must obtain explicit user confirmation between these two use cases.
 * These operations never parse PDFs and do not bypass vault session policy.
 */
class PrepareStatementReview @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(
        snapshotId: String,
        ownerId: `in`.growurdhan.core.model.OwnerId,
        sourceSha256: String,
        lines: List<StatementLine>,
    ): ReviewedStatement = vault.prepareStatementReview(snapshotId, ownerId, sourceSha256, lines)
}

class CommitReviewedStatement @Inject constructor(
    private val vault: VaultRepository,
) {
    suspend operator fun invoke(review: ReviewedStatement): `in`.growurdhan.core.model.SnapshotResult =
        vault.commitReviewedStatement(review)
}
