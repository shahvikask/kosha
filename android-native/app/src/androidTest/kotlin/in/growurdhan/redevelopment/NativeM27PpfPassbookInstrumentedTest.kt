package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativePpfSsyPassbookAccrual
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two genuine SQLCipher read-only owner-provenance and restart checks. */
@RunWith(AndroidJUnit4::class)
class NativeM27PpfPassbookInstrumentedTest {
    private val user = NativeCredentials.Enrollment("owner@example.org",
        "Fictional","Owner","Strong!Pass123","school","First School","pet","First Pet")
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir,"p11-passbook-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String) =
                File(root,"databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir() =
                File(root,"no_backup").also { it.mkdirs() }
            override fun getFilesDir() = File(root,"files").also { it.mkdirs() }
        }
    }
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart(); check(it.begin() && it.complete(true))
    }
    private fun enroll(ctx: Context): Pair<NativeAccountRepository,NativeBooksRepository> {
        val a = NativeAccountRepository(ctx,gate())
        a.enrollVerified(user,
            NativeRegistrationProof.Identity("ABCDE1234F","1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID,"2026-10-27T00:00:00Z",true))
        assertTrue(a.login(user.email,user.password))
        return a to NativeBooksRepository(a)
    }
    private fun year(owner: String, holding: String): NativePpfSsyPassbookAccrual.YearEvidence {
        val dates = listOf("2025-04-01","2025-07-01","2025-10-01","2026-01-01")
        val rates = listOf(700,720,740,760).mapIndexed { i,b ->
            NativePpfSsyPassbookAccrual.QuarterRate(LocalDate.parse(dates[i]),b,
                "fictional-rate-ref-$i")
        }
        fun deposit(date: String, n: Long, id: String) =
            NativePpfSsyPassbookAccrual.Deposit(owner,holding,LocalDate.parse(date),
                n,id,"fictional-statement-row-$id")
        val deposits = listOf(
            deposit("2025-04-05",120_000L,"1"),
            deposit("2025-04-06",230_000L,"2"),
            deposit("2025-05-04",50_000L,"3"),
            deposit("2025-07-05",100_000L,"4"),
            deposit("2025-07-06",200_000L,"5"),
            deposit("2026-03-31",300_000L,"6"))
        return NativePpfSsyPassbookAccrual.YearEvidence(owner,holding,
            NativePpfSsyPassbookAccrual.Scheme.PPF,LocalDate.parse("2026-03-31"),
            1_000_000L,"fictional-opening-statement",deposits,rates,
            2_116_012L,"fictional-closing-statement")
    }
    @Test fun ownerScopedPassbookComparisonDoesNotWriteAndRepeatsAcrossEncryptedRestart() {
        val ctx = sandbox()
        val (accounts,books) = enroll(ctx)
        val owner = books.read(user.email).people.single().id
        val holding = books.createHolding(user.email,NativeBooksRepository.NewHolding(
            owner,null,"Fictional PPF passbook","FIXED_INCOME","PPF",
            100_000_000L,1_000_000L,"2024-01-01",true))
        val prior = books.history(user.email,holding).map { it.id }
        val input = year(owner,holding)
        val result = books.previewPpfSsyPassbook(user.email,holding,listOf(input),
            LocalDate.parse("2026-10-27"))
        assertTrue(result.allEnteredClosingsMatch)
        assertEquals(116_012L,result.totalCalculatedInterestMinor)
        assertEquals(prior,books.history(user.email,holding).map { it.id })
        val before = books.read(user.email).holdings.single()
        accounts.onBackground()
        val again = NativeAccountRepository(ctx,gate())
        assertTrue(again.login(user.email,user.password))
        val reopened = NativeBooksRepository(again)
        val compared = reopened.previewPpfSsyPassbook(user.email,holding,listOf(input),
            LocalDate.parse("2026-10-27"))
        assertEquals(result,compared)
        assertEquals(prior,reopened.history(user.email,holding).map { it.id })
        assertEquals(before.marketPriceMinor,
            reopened.read(user.email).holdings.single().marketPriceMinor)
    }
    @Test fun foreignOwnerOrHoldingAndWrongSchemeFailWithoutPartiallyChangingSqlcipher() {
        val ctx = sandbox()
        val (_,books) = enroll(ctx)
        val first = books.read(user.email).people.single().id
        val other = books.createPerson(user.email,
            NativeBooksRepository.NewPerson("Other","Spouse"))
        val a = books.createHolding(user.email,NativeBooksRepository.NewHolding(
            first,null,"PPF A","FIXED_INCOME","PPF",100_000_000L,
            1_000_000L,"2024-01-01",true))
        val b = books.createHolding(user.email,NativeBooksRepository.NewHolding(
            other,null,"PPF B","FIXED_INCOME","PPF",100_000_000L,
            1_000_000L,"2024-01-01",true))
        val beforeA = books.history(user.email,a).map { it.id }
        val beforeB = books.history(user.email,b).map { it.id }
        val source = year(first,a)
        val invalid = listOf(
            source.copy(ownerId=other),
            source.copy(holdingId=b),
            source.copy(deposits=source.deposits.map {
                it.copy(ownerId=other) }),
            source.copy(scheme=NativePpfSsyPassbookAccrual.Scheme.SSY)
        )
        for (input in invalid) {
            assertThrows(IllegalArgumentException::class.java) {
                books.previewPpfSsyPassbook(user.email,a,listOf(input),
                    LocalDate.parse("2026-10-27"))
            }
            assertEquals(beforeA,books.history(user.email,a).map { it.id })
            assertEquals(beforeB,books.history(user.email,b).map { it.id })
        }
    }

    @Test fun sourcedWithdrawalPreviewIsReadOnlyAndUnreconciledAfterEncryptedRestart() {
        val ctx = sandbox()
        val (account, books) = enroll(ctx)
        val owner = books.read(user.email).people.single().id
        val holding = books.createHolding(user.email, NativeBooksRepository.NewHolding(
            owner, null, "Fictional PPF withdrawal", "FIXED_INCOME", "PPF",
            100_000_000L, 1_000_000L, "2024-01-01", true))
        val history = books.history(user.email, holding).map { it.id }
        val fixture = year(owner, holding).copy(withdrawals = listOf(
            NativePpfSsyPassbookAccrual.Withdrawal(owner, holding,
                LocalDate.parse("2025-07-07"), 400_000L, "withdrawal-1",
                "fictional-passbook-withdrawal-1")))
        val preview = books.previewPpfSsyPassbook(user.email, holding,
            listOf(fixture), LocalDate.parse("2026-10-27"))
        assertFalse(preview.allEnteredClosingsMatch)
        assertEquals(400_000L, preview.comparisons.single().withdrawalsMinor)
        assertEquals(1_300_000L,
            preview.comparisons.single().months[3].lowestFromFifthMinor)
        assertEquals(history, books.history(user.email, holding).map { it.id })
        account.onBackground()
        val again = NativeAccountRepository(ctx, gate())
        assertTrue(again.login(user.email, user.password))
        val reopened = NativeBooksRepository(again)
        assertEquals(preview, reopened.previewPpfSsyPassbook(user.email, holding,
            listOf(fixture), LocalDate.parse("2026-10-27")))
        assertEquals(history, reopened.history(user.email, holding).map { it.id })
    }

    @Test fun overlappingImmutableFinancialLedgerMovementRequiresSourceReconciliation() {
        val ctx = sandbox()
        val (account, books) = enroll(ctx)
        val owner = books.read(user.email).people.single().id
        val holding = books.createHolding(user.email, NativeBooksRepository.NewHolding(
            owner, null, "Fictional manual journal PPF", "FIXED_INCOME", "PPF",
            100_000_000L, 1_000_000L, "2024-01-01", true))
        val input = year(owner, holding)
        // This synthetic owner journal entry is NOT a linked issuer passbook row.
        val event = books.appendUserEvent(user.email, NativeBooksRepository.NewEvent(
            holding, "INTEREST", "2025-10-01", 0L, 0L, 100L))
        val original = books.history(user.email, holding).map { it.id }
        assertTrue(event in original)
        assertThrows(IllegalArgumentException::class.java) {
            books.previewPpfSsyPassbook(user.email, holding,
                listOf(input), LocalDate.parse("2026-10-27"))
        }
        assertEquals(original, books.history(user.email, holding).map { it.id })
        account.onBackground()
        val again = NativeAccountRepository(ctx, gate())
        assertTrue(again.login(user.email, user.password))
        val reopened = NativeBooksRepository(again)
        assertThrows(IllegalArgumentException::class.java) {
            reopened.previewPpfSsyPassbook(user.email, holding,
                listOf(input), LocalDate.parse("2026-10-27"))
        }
        assertEquals(original, reopened.history(user.email, holding).map { it.id })
    }
}
