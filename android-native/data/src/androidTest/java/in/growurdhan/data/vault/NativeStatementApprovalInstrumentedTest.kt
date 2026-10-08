package \`in\`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import \`in\`.growurdhan.core.model.*
import \`in\`.growurdhan.domain.finance.*
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical SQLCipher Android transaction checks: review must never write;
 * commit must reject state drift, tampering and conflicted owner/source data.
 */
@RunWith(AndroidJUnit4::class)
class NativeStatementApprovalInstrumentedTest {
    private lateinit var context: Context
    private lateinit var vault: SqlCipherVaultRepository
    private lateinit var folder: File
    private lateinit var alias: String
    private val owner = OwnerId("self")
    private val holdingId = HoldingId("h1")

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        folder = File(context.noBackupFilesDir, "m3-review-test-$suffix")
        alias = "in.growurdhan.review.$suffix"
        vault = SqlCipherVaultRepository(context, folder, alias)
    }

    @After fun cleanup(): Unit = runBlocking {
        vault.close()
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private suspend fun seed() {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic", "Self", true))
        vault.createHolding(Holding(
            holdingId, owner, null, "Synthetic Index", "MUTUAL_FUND",
            QuantityScaled(0), MoneyScaled(1_000_000),
        ))
    }

    private fun statement(
        qty: Long = 100_000_000,
        id: HoldingId = holdingId,
        ownerId: OwnerId = owner,
        date: String = "2026-09-30",
    ) = StatementLine(
        id, ownerId, LocalDate.parse(date), qty,
        MoneyScaled(1_000_000),
        // Only a real reported contribution may establish cost from a statement.
        MoneyScaled(if (qty == 0L) 0 else qty / 100_000_000 * 800_000),
    )

    @Test fun reviewDoesNotWriteAndApprovalImportsOpeningOnce(): Unit = runBlocking {
        seed()
        val review = vault.prepareStatementReview(
            "snapshot-a", owner, "a".repeat(64), listOf(statement()),
        )
        assertEquals(ReconciliationOutcome.NEW_POSITION, review.plan.lines.single().outcome)
        assertTrue(vault.events().isEmpty())
        assertEquals(SnapshotResult.IMPORTED, vault.commitReviewedStatement(review))
        val book = vault.readBook()
        assertEquals(1, book.events.size)
        assertEquals(LedgerKind.OPENING_BALANCE, book.events.single().kind)
        assertEquals(100_000_000L, book.events.single().quantityDeltaScaled)
        assertEquals(SnapshotResult.ALREADY_IMPORTED, vault.commitReviewedStatement(review))
        assertEquals(1, vault.health().eventCount)
        vault.close()
        vault.openOrCreate()
        assertEquals(book, vault.readBook())
    }

    @Test fun financialChangeAfterReviewRejectsStaleApprovalWithNoExtraEvents(): Unit = runBlocking {
        seed()
        val review = vault.prepareStatementReview(
            "snapshot-b", owner, "b".repeat(64), listOf(statement()),
        )
        vault.appendFinancialEvents(listOf(FinancialEvent(
            "manual-buy", holdingId, owner, FinancialEventKind.BUY,
            LocalDate.parse("2026-05-01"), 100_000_000, MoneyScaled(800_000),
            MoneyScaled(800_000),
        )))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { vault.commitReviewedStatement(review) }
        }
        assertEquals(listOf("manual-buy"), vault.events().map { it.id })
        // The failed approval did not reserve the SHA, so a new review remains possible.
        val next = vault.prepareStatementReview(
            "snapshot-b", owner, "b".repeat(64), listOf(statement()),
        )
        assertEquals(ReconciliationOutcome.MATCH, next.plan.lines.single().outcome)
    }

    @Test fun modifiedReceiptPlanAndSourceAreRejectedByHmac(): Unit = runBlocking {
        seed()
        val review = vault.prepareStatementReview(
            "snapshot-c", owner, "c".repeat(64), listOf(statement()),
        )
        val tamperedSource = review.copy(sourceLines = listOf(statement(qty = 300_000_000)))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { vault.commitReviewedStatement(tamperedSource) }
        }
        val tamperedPlan = review.copy(plan = review.plan.copy(warnings = listOf("changed")))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { vault.commitReviewedStatement(tamperedPlan) }
        }
        assertTrue(vault.readBook().events.isEmpty())
        // Original review remains usable and no failed attempt has written its fingerprint.
        assertEquals(SnapshotResult.IMPORTED, vault.commitReviewedStatement(review))
    }

    @Test fun conflictedOwnerCannotCommitObservationOrCreateLedgerEvents(): Unit = runBlocking {
        seed()
        val spouse = OwnerId("spouse")
        vault.createMember(FamilyMember(spouse, "Other", "Spouse", false))
        vault.createHolding(Holding(
            HoldingId("spouse-fund"), spouse, null, "Other Fund", "MUTUAL_FUND",
            QuantityScaled(0), MoneyScaled(1_000_000),
        ))
        val receipt = vault.prepareStatementReview(
            "snapshot-d", owner, "d".repeat(64),
            listOf(statement(id = HoldingId("spouse-fund"))),
        )
        assertEquals(ReconciliationOutcome.CONFLICT, receipt.plan.lines.single().outcome)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.commitReviewedStatement(receipt) }
        }
        assertEquals(0, vault.health().eventCount)
    }

    @Test fun zeroQuantitySnapshotIsObservationNotFalseBuy(): Unit = runBlocking {
        seed()
        val receipt = vault.prepareStatementReview(
            "snapshot-e", owner, "e".repeat(64),
            listOf(statement(qty = 0)),
        )
        assertEquals(ReconciliationOutcome.MATCH, receipt.plan.lines.single().outcome)
        assertTrue(receipt.plan.lines.single().proposedEvents.isEmpty())
        assertEquals(SnapshotResult.IMPORTED, vault.commitReviewedStatement(receipt))
        assertEquals(0, vault.health().eventCount)
        assertEquals(SnapshotResult.ALREADY_IMPORTED, vault.commitReviewedStatement(receipt))
    }

    @Test fun subsequentStatementOnlyCommitsDeltaAndCannotRewind(): Unit = runBlocking {
        seed()
        val first = vault.prepareStatementReview(
            "snapshot-f1", owner, "f".repeat(64), listOf(statement()),
        )
        assertEquals(SnapshotResult.IMPORTED, vault.commitReviewedStatement(first))
        val nextLine = statement(qty = 150_000_000, date = "2026-10-08").copy(
            cumulativeInvested = null,
        )
        val second = vault.prepareStatementReview(
            "snapshot-f2", owner, "1".repeat(64), listOf(nextLine),
        )
        assertEquals(ReconciliationOutcome.ADJUST_UP, second.plan.lines.single().outcome)
        assertEquals(50_000_000L, second.plan.lines.single().deltaQtyScaled)
        assertEquals(SnapshotResult.IMPORTED, vault.commitReviewedStatement(second))
        val rows = vault.events()
        assertEquals(2, rows.size)
        assertEquals(50_000_000L, rows.single { it.kind == LedgerKind.SNAPSHOT_ADJUSTMENT }.quantityDeltaScaled)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.prepareStatementReview(
                    "snapshot-old", owner, "2".repeat(64),
                    listOf(statement(date = "2026-01-01")),
                )
            }
        }
    }
}
