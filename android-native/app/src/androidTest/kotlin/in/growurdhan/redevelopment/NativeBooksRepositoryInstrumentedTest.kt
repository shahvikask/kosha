package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import `in`.growurdhan.model.NativeStatementCsv
import `in`.growurdhan.model.NativeAnnualInterestCredit
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real encrypted SQLCipher/Keystore records in an isolated Android test sandbox. */
@RunWith(AndroidJUnit4::class)
class NativeBooksRepositoryInstrumentedTest {
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "p01-p34-books-" + UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root, "files").also { it.mkdirs() }
        }
    }
    private val input = NativeCredentials.Enrollment("owner@example.org", "Owner", "Family",
        "Strong!Pass123", "school", "First School", "pet", "First Pet")
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun create(context: Context, admissionGate: NativeDeviceAdmission = gate()):
        Pair<NativeAccountRepository, NativeBooksRepository> {
        val accounts = NativeAccountRepository(context, admissionGate)
        accounts.enrollVerified(input,
            NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true))
        assertTrue(accounts.login(input.email, input.password))
        return accounts to NativeBooksRepository(accounts)
    }

    /**
     * Source-reconciliation negative fixture: normal production metadata edits must reject
     * post-ledger custody transfers. Simulate a legacy/external migration drift only within
     * this isolated encrypted test vault to exercise independent stale-preview rejection.
     */
    private fun simulateLegacyCustodyDrift(accounts: NativeAccountRepository,
        books: NativeBooksRepository, holdingId: String, newCustodyId: String) {
        assertThrows(IllegalArgumentException::class.java) {
            books.updateHoldingMetadata(input.email, holdingId, newCustodyId,
                null, null, null)
        }
        accounts.withUnlockedVault(input.email) { db ->
            db.execSQL("UPDATE holding SET account_id=? WHERE holding_id=?",
                arrayOf<Any>(newCustodyId, holdingId))
        }
    }

    @Test fun p24RecordedFinancialCoverageIsExactOwnerScopedAndRestartSafe() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val foreign = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Other member", "Spouse"))
        val date = LocalDate.now().toString()
        val own = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Own liquid equity", "EQUITY", "Other",
            100_000_000L, 30_000L, date, true,
            openingUnitPriceMinor = 10_000L))
        val sale = books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            own, "SELL", date, 50_000_000L, 20_000L, 10_000L))
        assertTrue(sale.isNotBlank())
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            own, "DIVIDEND", date, 0L, 0L, 12_000L))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Unknown acquisition basis", "EQUITY", "Other",
            100_000_000L, 40_000L, date, false))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Old and stale price", "EQUITY", "Other",
            100_000_000L, 50_000L, "2025-01-01", true))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            foreign, null, "Foreign price must stay hidden", "EQUITY", "Other",
            100_000_000L, 990_000L, date, true))
        val before = books.read(input.email)
        val history = books.history(input.email, own)
        val ownReport = books.recordedFinancialReport(input.email, owner)
        val year = LocalDate.now().year
        assertEquals(java.math.BigInteger.valueOf(55_000L),
            ownReport.pricedAssetMinor)
        assertEquals(java.math.BigInteger.valueOf(5_000L),
            ownReport.pricedKnownBasisMinor)
        assertEquals(java.math.BigInteger.valueOf(10_000L),
            ownReport.knownUnrealizedMinor)
        assertEquals(java.math.BigInteger.valueOf(5_000L),
            ownReport.realizedKnownMinor)
        assertEquals(java.math.BigInteger.valueOf(12_000L),
            ownReport.incomeByCalendarYear[year])
        assertEquals(java.math.BigInteger.valueOf(5_000L),
            ownReport.realizedByCalendarYear[year])
        assertEquals(java.math.BigInteger.valueOf(55_000L),
            ownReport.pricedCategories["EQUITY"])
        assertEquals(1, ownReport.unpricedActiveAssets)
        assertEquals(1, ownReport.pricedUnknownBasisAssets)
        assertEquals(2, ownReport.pricedAssetCount)
        assertEquals(1, ownReport.countKnownBasis)
        assertEquals(0, ownReport.realizedUnknownLots)
        val stranger = books.recordedFinancialReport(input.email, foreign)
        assertEquals(java.math.BigInteger.valueOf(990_000L),
            stranger.pricedAssetMinor)
        assertTrue(stranger.incomeByCalendarYear.isEmpty())
        assertTrue(stranger.realizedByCalendarYear.isEmpty())
        assertEquals(0, stranger.unpricedActiveAssets)
        assertThrows(IllegalArgumentException::class.java) {
            books.recordedFinancialReport(input.email, "unregistered-member")
        }
        assertEquals(before.holdings, books.read(input.email).holdings)
        assertEquals(history, books.history(input.email, own))
        accounts.onBackground()
        assertThrows(Exception::class.java) {
            books.recordedFinancialReport(input.email, owner)
        }
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val latest = NativeBooksRepository(reopened)
        assertEquals(ownReport, latest.recordedFinancialReport(input.email, owner))
        assertEquals(history, latest.history(input.email, own))
        // Encrypted ledger events are immutable. A corrupt owner reassignment
        // must be rejected at the SQLCipher boundary, not simulated by
        // bypassing the production append-only trigger in this test.
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            reopened.withUnlockedVault(input.email) { db ->
                db.execSQL("UPDATE ledger_event SET entity_id=? " +
                    "WHERE ledger_event_id=?", arrayOf<Any>(foreign, sale))
            }
        }
        assertEquals(ownReport, latest.recordedFinancialReport(input.email, owner))
        assertEquals(history, latest.history(input.email, own))
        assertEquals(java.math.BigInteger.valueOf(990_000L),
            latest.recordedFinancialReport(input.email, foreign).pricedAssetMinor)
        // Secondary report-layer defense: a synthetic corrupt/migrated row
        // with a foreign event must also fail closed independently of SQLCipher.
        val poisoned = NativeRecordedFinanceReport.Row(
            holdingId = own, ownerId = owner, category = "EQUITY",
            archived = false, needsReview = false, quantityScaled = 100_000_000L,
            priceMinor = 10_000L, priceDate = date,
            priceSource = "Explicit recorded test valuation",
            events = listOf(`in`.growurdhan.model.NativePortfolioMath.Event(
                "foreign-event", foreign, own, "DIVIDEND", date,
                0L, 0L, 12_000L, 0L, null, true))
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeRecordedFinanceReport.summarize(listOf(poisoned), owner, LocalDate.now())
        }
    }

    @Test fun p24ReviewedHybridSplitIsEncryptedScopedAndDoesNotPostFinancialEvents() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val foreign = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Member Alpha", "Spouse"))
        val day = LocalDate.now().toString()
        val own = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Reviewed hybrid", "MUTUAL_FUND", "Hybrid fund",
            100_000_000L, 1_010_000L, day, true))
        val unknown = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Unpriced hybrid", "MUTUAL_FUND", "Hybrid fund",
            100_000_000L, 0L, day, false))
        val foreignId = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            foreign, null, "Foreign hybrid", "MUTUAL_FUND", "Hybrid fund",
            100_000_000L, 999_0000L, day, true))
        val nonHybrid = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Nonhybrid index", "MUTUAL_FUND", "Index / ETF",
            100_000_000L, 1000L, day, false))
        val before = books.read(input.email)
        val history = books.history(input.email, own)
        val split = "EQUITY:6500,DEBT:3500"
        val reviewed = books.saveHybridExposure(input.email, own, owner,
            day, "Owner-reviewed stated factsheet", false, split)
        assertEquals(2, reviewed.slices.size)
        books.saveHybridExposure(input.email, unknown, owner,
            day, "Owner estimated source", true, split)
        books.saveHybridExposure(input.email, foreignId, foreign,
            day, "Other member review", true, "GOLD:10000")
        val ownReview = books.hybridExposureReviews(input.email, owner)
        assertEquals(setOf(own, unknown), ownReview.map { it.holdingId }.toSet())
        assertFalse(ownReview.any { it.ownerId == foreign })
        assertEquals(listOf(foreignId),
            books.hybridExposureReviews(input.email, foreign).map { it.holdingId })
        val totals = NativeHybridExposureReview.report(
            books.read(input.email).holdings, ownReview, owner, LocalDate.now())
        assertEquals(java.math.BigInteger.valueOf(1_010_000L),
            totals.summary.knownValueMinor)
        assertEquals(java.math.BigInteger.ZERO, totals.summary.estimatedValueMinor)
        assertEquals(1, totals.excludedStaleOrUnknown)
        assertEquals(0, totals.awaitingReview)
        assertEquals(java.math.BigInteger.valueOf(656_500L),
            totals.summary.totals["EQUITY"])
        assertEquals(java.math.BigInteger.valueOf(353_500L),
            totals.summary.totals["DEBT"])
        assertEquals(before.holdings, books.read(input.email).holdings)
        assertEquals(history, books.history(input.email, own))
        for (bad in listOf("EQUITY:9999", "EQUITY:5000,EQUITY:5000",
            "UNKNOWN:10000", "DEBT:-100", "EQUITY:0,DEBT:10000")) {
            assertThrows(IllegalArgumentException::class.java) {
                books.saveHybridExposure(input.email, own, owner, day,
                    "Owner-reviewed stated factsheet", false, bad)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.saveHybridExposure(input.email, own, foreign, day,
                "Owner-reviewed stated factsheet", false, split)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.saveHybridExposure(input.email, nonHybrid, owner, day,
                "Owner-reviewed stated factsheet", false, split)
        }
        assertThrows(Exception::class.java) {
            books.saveHybridExposure(input.email, own, owner,
                LocalDate.now().plusDays(1).toString(), "Owner review", false, split)
        }
        assertEquals(ownReview, books.hybridExposureReviews(input.email, owner))
        accounts.onBackground()
        assertThrows(Exception::class.java) {
            books.hybridExposureReviews(input.email, owner)
        }
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val restored = NativeBooksRepository(reopened)
        assertEquals(ownReview, restored.hybridExposureReviews(input.email, owner))
        reopened.withUnlockedVault(input.email) { db ->
            db.execSQL("UPDATE setting SET value='corrupt-review' WHERE key=?",
                arrayOf<Any>(NativeHybridExposureReview.key(own)))
        }
        assertThrows(Exception::class.java) {
            restored.hybridExposureReviews(input.email, owner)
        }
        restored.deleteHybridExposure(input.email, own, owner)
        assertEquals(listOf(unknown),
            restored.hybridExposureReviews(input.email, owner).map { it.holdingId })
        assertEquals(before.holdings, restored.read(input.email).holdings)
        assertEquals(history, restored.history(input.email, own))
    }

    @Test fun propertyPreviewFailsClosedWithoutAuthenticatedRegistryAndValuation() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val custody = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "OTHER", "Synthetic registry custody"))
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, custody, "Synthetic residential property", "REAL_ESTATE", "Residential",
            0L, 0L, "2025-01-01", false))
        val claim = `in`.growurdhan.model.NativeSharedPropertyAggregation.Claim(
            holding, owner, "synthetic-registry-1", "missing-source-document",
            "a".repeat(64), LocalDate.parse("2025-01-01"), "50",
            100_000L, "unsupported-appraisal", LocalDate.parse("2025-02-01"))
        val before = books.read(input.email)
        val beforeHistory = books.history(input.email, holding)
        try {
            books.inspectPropertyClaims(input.email, listOf(claim),
                LocalDate.parse("2025-03-01"))
            fail("Unsupported title and appraisal must not publish a property preview")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("source"))
        }
        try {
            books.inspectPropertyClaims(input.email, listOf(claim.copy(ownerId="foreign-owner")),
                LocalDate.parse("2025-03-01"))
            fail("Cross-owner property claim must be refused")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("owner"))
        }
        val after = books.read(input.email)
        assertEquals(before.holdings, after.holdings)
        assertEquals(beforeHistory, books.history(input.email, holding))
        // Reject duplicate holding claims and contradictory gross valuations.
        try {
            books.inspectPropertyClaims(input.email, listOf(claim, claim),
                LocalDate.parse("2025-03-01"))
            fail("Duplicate property holding must reject")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("Duplicate"))
        }
        try {
            books.inspectPropertyClaims(input.email,
                listOf(claim, claim.copy(holdingId="another-holding",
                    ownerId="another-owner", grossPropertyValueMinor=200_000L)),
                LocalDate.parse("2025-03-01"))
            fail("Conflicting property valuations must reject")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("conflict"))
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val persisted = NativeBooksRepository(reopened)
        assertEquals(before.holdings, persisted.read(input.email).holdings)
        assertEquals(beforeHistory, persisted.history(input.email, holding))
        assertThrows(IllegalArgumentException::class.java) {
            persisted.inspectPropertyClaims(input.email, listOf(claim),
                LocalDate.parse("2025-03-01"))
        }
    }

    @Test fun directPropertyClaimWithoutOwnerCustodyRefusesBeforeSourceLookup() {
        val (_, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val unlinked = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic uncustodied land", "REAL_ESTATE", "Land",
            0L, 0L, "2025-01-01", false))
        val proposed = `in`.growurdhan.model.NativeSharedPropertyAggregation.Claim(
            unlinked, owner, "synthetic-unverified-land-id", "not-a-real-source",
            "c".repeat(64), LocalDate.parse("2025-01-01"), "100",
            30_000L, "not-an-appraisal", LocalDate.parse("2025-02-01"))
        val before = books.read(input.email).holdings
        try {
            books.inspectPropertyClaims(input.email, listOf(proposed),
                LocalDate.parse("2025-03-01"))
            fail("Unlinked direct property must not be accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("custody"))
        }
        assertEquals(before, books.read(input.email).holdings)
        assertTrue(books.history(input.email, unlinked).isEmpty())
    }

    @Test fun listedReitCannotBeMisrepresentedAsRegisteredRealProperty() {
        val context = sandbox()
        val (_, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val custody = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "OTHER", "Synthetic registry custody"))
        val reit = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, custody, "Fictional listed REIT", "REAL_ESTATE", "REIT",
            0L, 0L, "2025-01-01", false))
        val claim = `in`.growurdhan.model.NativeSharedPropertyAggregation.Claim(
            reit, owner, "fictional-land-registry", "fictional-document",
            "b".repeat(64), LocalDate.parse("2025-01-01"), "100",
            100_000L, "unsupported-valuation", LocalDate.parse("2025-02-01"))
        val before = books.read(input.email).holdings
        try {
            books.inspectPropertyClaims(input.email, listOf(claim),
                LocalDate.parse("2025-03-01"))
            fail("A listed REIT is not authenticated direct real estate title")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("not real estate"))
        }
        assertEquals(before, books.read(input.email).holdings)
        assertTrue(books.history(input.email, reit).isEmpty())
    }

    @Test fun genuineEncryptedRegistrationStoresCurrentVersionedTermsAndPrimaryOwner() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val snapshot = books.read(input.email)
        assertEquals(1, snapshot.people.size)
        assertEquals("Self", snapshot.people.single().relation)
        assertTrue(snapshot.people.single().isPrimary)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.terms.text_id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(NativeRegistrationProof.TERMS_TEXT_ID, it.getString(0))
            }
            db.rawQuery("SELECT pan_masked,date_of_birth FROM vault_account", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("ABCDE****F", it.getString(0))
                assertEquals("1990-06-12", it.getString(1))
            }
        }
    }

    @Test fun editingExistingMinorRefusesIneligibleGuardianWithoutWrites() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val minor = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Synthetic Minor", "Child", dob = "2020-02-01", guardianId = primary))
        val huf = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Synthetic HUF", "HUF", kind = "HUF"))
        val otherMinor = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Other Minor", "Child", dob = "2021-02-01", guardianId = primary))
        for (guardian in listOf(huf, otherMinor)) {
            assertThrows(IllegalArgumentException::class.java) {
                books.updatePerson(input.email, minor, NativeBooksRepository.NewPerson(
                    "Changed Minor", "Child", dob = "2020-02-01", guardianId = guardian))
            }
        }
        val same = books.read(input.email).people.single { it.id == minor }
        assertEquals("Synthetic Minor", same.name)
        assertTrue(same.active)
        books.updatePerson(input.email, minor, NativeBooksRepository.NewPerson(
            "Renamed Minor", "Child", guardianId = primary))
        assertEquals("Renamed Minor", books.read(input.email).people.single { it.id == minor }.name)
    }

    @Test fun guardianMustNotBeChildOrNonindividualAndFailurePreservesFamily() {
        val (_, books) = create(sandbox())
        val adult = books.read(input.email).people.single().id
        val child = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Synthetic Child", "Child", dob = "2020-01-01", guardianId = adult))
        val huf = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Synthetic HUF", "HUF", kind = "HUF"))
        val initial = books.read(input.email).people.size
        for (guardian in listOf(child, huf)) {
            assertThrows(IllegalArgumentException::class.java) {
                books.createPerson(input.email, NativeBooksRepository.NewPerson(
                    "Second Minor", "Child", dob = "2021-01-01", guardianId = guardian))
            }
        }
        assertEquals(initial, books.read(input.email).people.size)
    }

    @Test fun custodyEditRejectsTruncatedIdentifiersWithoutChangingEncryptedAccount() {
        val (_, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val account = books.createCustody(input.email, NativeBooksRepository.NewCustody(
            owner, "DEMAT", "Synthetic Custodian", identifierMasked = "****6789",
            dpId = "DP123", clientId = "CLIENT123", nominee = "Example"))
        assertThrows(IllegalArgumentException::class.java) {
            books.updateCustody(input.email, account, NativeBooksRepository.NewCustody(
                owner, "DEMAT", "Changed Custodian", dpId = "A".repeat(101)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateCustody(input.email, account, NativeBooksRepository.NewCustody(
                owner, "DEMAT", "Changed Custodian", nominee = "N".repeat(129)))
        }
        val original = books.read(input.email).custody.single { it.id == account }
        assertEquals("Synthetic Custodian", original.custodian)
        assertEquals("Example", original.nominee)
    }

    @Test fun custodyIdentifiersAndNomineeAreNeverSilentlyTruncated() {
        val (_, books) = create(sandbox())
        val person = books.read(input.email).people.single().id
        val before = books.read(input.email).custody.size
        assertThrows(IllegalArgumentException::class.java) {
            books.createCustody(input.email, NativeBooksRepository.NewCustody(
                person, "DEMAT", "Artificial Depository", dpId = "A".repeat(101),
                clientId = "12345678"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.createCustody(input.email, NativeBooksRepository.NewCustody(
                person, "DEMAT", "Artificial Depository", nominee = "N".repeat(129)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.createCustody(input.email, NativeBooksRepository.NewCustody(
                person, "DEMAT", "Artificial Depository", clientId = "123" + 10.toChar() + "456"))
        }
        assertEquals(before, books.read(input.email).custody.size)
    }

    @Test fun familyCustodyHoldingLedgerAndPriceSurviveEncryptedReopen() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val member = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Family Member", "Spouse", panForStatements = "PQRST1234F"))
        val custody = books.createCustody(input.email, NativeBooksRepository.NewCustody(
            member, "DEMAT", "Depository", identifierMasked = "****1234"))
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            member, custody, "Example Equity", "EQUITY", "Large cap",
            100_000_000L, 150_000L, "2024-01-01", true))
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2025-02-01", 40_000_000L, 200_000L, 80_000L, 0L))
        books.recordManualPrice(input.email, holding, 220_000L, "2025-02-02")
        val current = books.read(input.email).holdings.single()
        assertEquals(60_000_000L, current.quantityScaled)
        assertEquals(90_000L, current.knownCostMinor)
        assertEquals("USER_OVERRIDE", current.priceSource)
        assertEquals(2, books.history(input.email, holding).size)
        accounts.onBackground()
        val restarted = NativeAccountRepository(context, gate())
        assertTrue(restarted.login(input.email, input.password))
        val persisted = NativeBooksRepository(restarted).read(input.email)
        assertEquals("PQRST****F", persisted.people.single { it.id == member }.maskedPan) // Never output full PAN in summaries.
        assertEquals(60_000_000L, persisted.holdings.single().quantityScaled)
        assertEquals(220_000L, persisted.holdings.single().marketPriceMinor)
    }

    @Test fun wrongOwnerAndPrimaryDeletionFailWithoutChangingEncryptedData() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        try {
            books.archivePerson(input.email, primary)
            fail("Primary member cannot be archived")
        } catch (_: IllegalArgumentException) { }
        val member = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Another Member", "Spouse"))
        val foreign = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Third Member", "Parent"))
        val account = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(member, "FOLIO", "Mutual Fund"))
        try {
            books.createHolding(input.email, NativeBooksRepository.NewHolding(
                foreign, account, "Invalid", "MUTUAL_FUND", "Index / ETF",
                100_000_000L, 10_000L, "2025-01-01", true))
            fail("Cross-owner custody must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, books.read(input.email).holdings.size)
    }

    @Test fun oversellRollsBackAndLedgerTriggerPreventsMutation() {
        val (accounts, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Test Units", "MUTUAL_FUND", "Index / ETF",
            100_000_000L, 10_000L, "2024-01-01", true))
        val original = books.history(input.email, holding).single().id
        try {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                holding, "SELL", "2025-01-01", 200_000_000L, 10_000L, 20_000L))
            fail("Overselling cannot commit")
        } catch (_: IllegalArgumentException) { }
        assertEquals(1, books.history(input.email, holding).size)
        try {
            accounts.withUnlockedVault(input.email) { db ->
                db.execSQL("UPDATE ledger_event SET quantity_scaled=0 WHERE ledger_event_id=?",
                    arrayOf<Any>(original))
            }
            fail("Immutability trigger must abort")
        } catch (_: Exception) { }
        assertEquals(100_000_000L, books.read(input.email).holdings.single().quantityScaled)
    }

    @Test fun emptyHoldingAndUnknownOpeningCostAreNotInvented() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Empty", "GOLD_SGB", "Digital gold", 0L, 0L, "2024-01-01", false))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Unknown Cost", "EQUITY", "Other",
            100_000_000L, 30_000L, "2024-01-01", false))
        val holdings = books.read(input.email).holdings
        assertEquals(0L, holdings.single { it.name == "Empty" }.quantityScaled)
        assertTrue(holdings.single { it.name == "Unknown Cost" }.unknownCost)
    }
    @Test fun currentTermsAndOriginalAccountIdentityAreRequiredForAllNewVaultCreation() {
        val context = sandbox()
        val repo = NativeAccountRepository(context, gate())
        assertThrows(IllegalStateException::class.java) { repo.enroll(input) }
        assertFalse(context.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertThrows(IllegalArgumentException::class.java) {
            repo.enrollVerified(input, NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12"),
                NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                    NativeRegistrationProof.TERMS_TEXT_ID,
                    "2026-10-19T10:00:00Z", false))
        }
        assertFalse(context.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

    @Test fun existingEncryptedVaultNeedsPasswordAndFullCurrentTermsBeforeReunlock() {
        val context = sandbox()
        val (repo, books) = create(context)
        repo.withUnlockedVault(input.email) { db ->
            db.execSQL("DELETE FROM setting WHERE key='m25.terms.text_id'")
        }
        repo.lock()
        assertEquals(NativeAccountRepository.SignInResult.TERMS_REQUIRED,
            repo.loginResult(input.email, input.password))
        assertThrows(IllegalStateException::class.java) { books.read(input.email) }
        assertEquals(NativeAccountRepository.SignInResult.DENIED,
            repo.loginResult(input.email, "incorrect", NativeRegistrationProof.Consent(
                NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true)))
        val consent = NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
            NativeRegistrationProof.TERMS_TEXT_ID, "2026-10-19T10:00:00Z", true)
        assertEquals(NativeAccountRepository.SignInResult.UNLOCKED,
            repo.loginResult(input.email, input.password, consent))
        assertEquals(1, books.read(input.email).people.size)
        repo.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT value FROM setting WHERE key='m25.terms.text_id'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(NativeRegistrationProof.TERMS_TEXT_ID, it.getString(0))
            }
        }
    }

    @Test fun nomineeGoalAndCustodyLifecycleSurviveEncryptedRestartWithoutOwnerLeakage() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val primary = books.read(input.email).people.single().id
        val other = books.createPerson(input.email, NativeBooksRepository.NewPerson(
            "Synthetic Relative", "Parent"))
        val custody = books.createCustody(input.email, NativeBooksRepository.NewCustody(
            primary, "DEMAT", "Original Custodian", nominee = "Initial Nominee"))
        books.updateCustody(input.email, custody, NativeBooksRepository.NewCustody(
            primary, "DEMAT", "Updated Custodian", nominee = "Updated Nominee"))
        val goal = books.createGoal(input.email, primary, "Synthetic Goal", 200000L,
            "2032-01-01", "HIGH")
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, custody, "Synthetic Holding", "MUTUAL_FUND", "Index / ETF",
            0L, 10000L, "2024-01-01", false))
        books.assignGoal(input.email, holding, goal)
        val foreign = books.createGoal(input.email, other, "Foreign Goal", 100000L,
            "2032-01-01", "LOW")
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(input.email, holding, foreign)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.removeUnlinkedGoal(input.email, goal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.closeCustody(input.email, other, custody, "2026-10-24")
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val reopenedBooks = NativeBooksRepository(reopened)
        val before = reopenedBooks.read(input.email)
        assertEquals("Updated Nominee", before.custody.single { it.id == custody }.nominee)
        assertEquals("Updated Custodian", before.custody.single { it.id == custody }.custodian)
        assertEquals(primary, before.holdings.single { it.id == holding }.personId)
        assertEquals(primary, before.goals.single { it.id == goal }.personId)
        assertEquals(2, before.goals.size)
        reopenedBooks.closeCustody(input.email, primary, custody, "2026-10-24")
        assertThrows(IllegalArgumentException::class.java) {
            reopenedBooks.createHolding(input.email, NativeBooksRepository.NewHolding(
                primary, custody, "New Holding", "MUTUAL_FUND", "Index / ETF",
                0L, 10000L, "2024-01-01", false))
        }
        assertEquals(1, reopenedBooks.read(input.email).holdings.size)
    }

    @Test fun identityEditsGoalsAndCustodyClosureNeverReassignExistingBooks() {
        val (_, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val member = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Parent Initial", "Parent"))
        books.updatePerson(input.email, member,
            NativeBooksRepository.NewPerson("Parent Updated", "Parent",
                panForStatements = "PQRST1234F"))
        assertEquals("PQRST****F", books.read(input.email).people.single {
            it.id == member }.maskedPan)
        val goal = books.createGoal(input.email, member, "College", 1_000_000L,
            "2032-01-01", "HIGH")
        books.updateGoal(input.email, goal, member, "Education", 2_000_000L,
            "2031-01-01", "HIGH")
        assertEquals(2_000_000L, books.read(input.email).goals.single {
            it.id == goal }.targetMinor)
        val acct = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(member, "DEMAT", "Depository"))
        books.updateCustody(input.email, acct,
            NativeBooksRepository.NewCustody(member, "DEMAT", "New Depository"))
        assertEquals("New Depository", books.read(input.email).custody.single {
            it.id == acct }.custodian)
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            member, acct, "Fund units", "MUTUAL_FUND", "Index / ETF",
            100_000_000L, 10_000L, "2024-01-01", true))
        books.assignGoal(input.email, holding, goal)
        assertThrows(IllegalArgumentException::class.java) {
            books.updatePerson(input.email, member,
                NativeBooksRepository.NewPerson("Changed classification", "Spouse"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.updateCustody(input.email, acct,
                NativeBooksRepository.NewCustody(member, "BANK", "Changed custody"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.removeUnlinkedGoal(input.email, goal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.closeCustody(input.email, member, acct, "2026-10-01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.archivePerson(input.email, member)
        }
        val someoneElsesGoal = books.createGoal(input.email, primary, "Different",
            100_000L, "2027-01-01", "LOW")
        assertThrows(IllegalArgumentException::class.java) {
            books.assignGoal(input.email, holding, someoneElsesGoal)
        }
        assertEquals(member, books.read(input.email).holdings.single().personId)
    }

    @Test fun correctionAndClosingPositionKeepImmutableHistoryAndArchiveOnlyAtZero() {
        val (accounts, books) = create(sandbox())
        val primary = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Split-adjusted stock", "EQUITY", "Large cap",
            100_000_000L, 15_000L, "2024-01-01", true))
        val opening = books.history(input.email, holding).single().id
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "CORRECTION", "2024-01-02",
            200_000_000L, 15_000L, 30_000L, supersedes = opening))
        assertEquals(200_000_000L, books.read(input.email).holdings.single().quantityScaled)
        assertThrows(IllegalArgumentException::class.java) {
            books.setHoldingArchived(input.email, holding, true)
        }
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2025-04-01", 200_000_000L, 17_000L, 34_000L))
        books.setHoldingArchived(input.email, holding, true)
        assertTrue(books.read(input.email).holdings.single().archived)
        assertThrows(IllegalArgumentException::class.java) {
            books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
                holding, "BUY", "2025-04-02", 100_000_000L, 10_000L, 10_000L))
        }
        assertEquals(3, books.history(input.email, holding).size)
        books.setHoldingArchived(input.email, holding, false)
        assertFalse(books.read(input.email).holdings.single().archived)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM ledger_event WHERE holding_id=?",
                arrayOf(holding)).use {
                assertTrue(it.moveToFirst())
                assertEquals(3L, it.getLong(0))
            }
        }
    }

    @Test fun explicitFreeOpeningRemainsKnownZeroCostAcrossEncryptedRestart() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val primary = books.read(input.email).people.single().id
        val id = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Genuine zero cost gift", "EQUITY", "Other",
            100_000_000L, 0L, "2024-01-01", true))
        assertFalse(books.read(input.email).holdings.single().unknownCost)
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val persisted = NativeBooksRepository(reopened).read(input.email).holdings.single()
        assertEquals(id, persisted.id)
        assertFalse(persisted.unknownCost)
        assertEquals(0L, persisted.knownCostMinor)
    }

    @Test fun specialistMetadataAndIndependentAcquisitionCostPersistInSqlcipher() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val primary = books.read(input.email).people.single().id
        val id = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            primary, null, "Synthetic RBI bond", "FIXED_INCOME", "Bond / GOI",
            100_000_000L, 50_000L, "2024-01-01", true,
            isin = "INE000000001", detailsJson = """{"compounding":"HALF_YEARLY"}""",
            openingUnitPriceMinor = 40_000L, institutionName = "Government Issuer",
            accountNumberMasked = "****1234", maturityDate = "2030-01-01",
            couponRateBps = 825, interestRateBps = 825, monthlyEmiMinor = 10000L,
            emiStatus = "PAYING", remainingEmiCount = 18, location = "India"))
        assertEquals(40_000L, books.read(input.email).holdings.single().knownCostMinor)
        assertEquals(50_000L, books.read(input.email).holdings.single().marketPriceMinor)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT coupon_rate_bps,interest_rate_bps,monthly_emi_minor," +
                "emi_status,remaining_emi_count,details_json,institution_name,maturity_date " +
                "FROM holding WHERE holding_id=?", arrayOf(id)).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(825, c.getInt(0))
                assertEquals(825, c.getInt(1))
                assertEquals(10000L, c.getLong(2))
                assertEquals("PAYING", c.getString(3))
                assertEquals(18, c.getInt(4))
                assertEquals("""{"compounding":"HALF_YEARLY"}""", c.getString(5))
                assertEquals("Government Issuer", c.getString(6))
                assertEquals("2030-01-01", c.getString(7))
            }
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        assertEquals(40_000L, NativeBooksRepository(reopened).read(input.email)
            .holdings.single().knownCostMinor)
    }

    @Test fun emiCatchUpPostsOnceAndUpdatesCursorAtomicallyAcrossEncryptedReopen() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(input.email).people.single().id
        val loan = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic family loan", "LIABILITY", "Home loan",
            0L, 25_000L, "2025-01-01", knownOpeningCost = false))
        books.updateEmiSchedule(input.email, loan, "PAYING", 10_000L, "2025-01-31", 5)
        val first = books.catchUpEmi(input.email, loan, "2025-02-28")!!
        assertEquals(2, first.payments.size)
        assertEquals(5_000L, first.outstandingMinor)
        assertEquals("2025-03-28", first.nextDueDate)
        assertNull(books.catchUpEmi(input.email, loan, "2025-02-28"))
        assertEquals(2, books.history(input.email, loan).size)
        accounts.onBackground()

        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        val fresh = NativeBooksRepository(reopened)
        val finalPlan = fresh.catchUpEmi(input.email, loan, "2025-03-31")!!
        assertEquals(1, finalPlan.payments.size)
        assertEquals(5_000L, finalPlan.payments.single().amountMinor)
        assertEquals("STOPPED", finalPlan.status)
        assertEquals(3, fresh.history(input.email, loan).size)
        assertNull(fresh.catchUpEmi(input.email, loan, "2025-07-31"))
    }

    private fun csvSnapshot(pan: String = "ABCDE1234F",
        date: String = "2025-03-31", qty: Long = 150_000_000L,
        isin: String = "INE000000001"): ByteArray =
        ("owner_pan,statement_date,isin,folio,quantity_scaled,unit_price_minor,current_value_minor\\n" +
            "$pan,$date,$isin,,$qty,12500,18750\\n")
            .replace("\\n", "\n").toByteArray(Charsets.UTF_8)

    @Test fun reviewedCsvSnapshotIsOwnerBoundIdempotentAndNotATaxablePurchase() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic ISIN holding", "EQUITY", "Large cap",
            100_000_000L, 12_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        val bytes = csvSnapshot()
        val reviewed = importer.inspect(input.email, bytes)
        assertEquals(1, reviewed.matched.size)
        assertEquals(NativeStatementImportRepository.Action.APPLY_SNAPSHOT,
            reviewed.matched.single().action)
        assertEquals(50_000_000L, reviewed.matched.single().delta)
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "example.csv", bytes, reviewed, false)
        }
        assertEquals(1, books.history(input.email, holding).size)
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "example.csv", csvSnapshot(qty = 200_000_000L),
                reviewed, true)
        }
        val receipt = importer.approve(input.email, "example.csv", bytes, reviewed, true)
        assertFalse(receipt.duplicate)
        assertEquals(1, receipt.positions)
        assertEquals(1, receipt.adjustments)
        assertEquals(150_000_000L, books.read(input.email).holdings.single().quantityScaled)
        assertEquals(2, books.history(input.email, holding).size)
        assertEquals("SNAPSHOT_ADJUSTMENT", books.history(input.email, holding).last().kind)
        assertEquals("CSV_STATEMENT", books.read(input.email).holdings.single().priceSource)
        assertEquals(1, books.read(input.email).imports.size)
        assertEquals(1, books.read(input.email).documents.size)
        assertTrue(importer.inspect(input.email, bytes).duplicate)
        assertTrue(importer.approve(input.email, "example.csv", bytes, reviewed, true).duplicate)
        assertEquals(2, books.history(input.email, holding).size)
        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        val saved = NativeBooksRepository(reopened).read(input.email)
        assertEquals(150_000_000L, saved.holdings.single().quantityScaled)
        assertEquals(1, saved.imports.size)
    }

    @Test fun csvStatementRequiresExactPanAndIsinAndCannotOverrideFutureLedgerOrPrices() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Known security", "EQUITY", "Other",
            100_000_000L, 12_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        for (invalid in listOf(
            csvSnapshot(pan = "PQRST1234F"),
            csvSnapshot(isin = "INE000000002"))) {
            assertThrows(IllegalArgumentException::class.java) {
                importer.inspect(input.email, invalid)
            }
        }
        val current = importer.inspect(input.email, csvSnapshot())
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(h, "BUY",
            "2025-04-01", 100_000_000L, 10_000L, 10_000L))
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "verified.csv", csvSnapshot(), current, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        assertEquals(2, books.history(input.email, h).size)
    }

    @Test fun importingEarlierSnapshotNeverRewindsLaterStatementPosition() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Historical security", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        val latestBytes = csvSnapshot(date = "2025-03-31", qty = 150_000_000L)
        val latest = importer.inspect(input.email, latestBytes)
        importer.approve(input.email, "march.csv", latestBytes, latest, true)
        val oldBytes = csvSnapshot(date = "2025-02-28", qty = 125_000_000L)
        val old = importer.inspect(input.email, oldBytes)
        assertEquals(NativeStatementImportRepository.Action.HISTORICAL_ONLY,
            old.matched.single().action)
        val receipt = importer.approve(input.email, "february.csv", oldBytes, old, true)
        assertEquals(0, receipt.adjustments)
        assertEquals(150_000_000L, books.read(input.email).holdings.single().quantityScaled)
        assertEquals(2, books.read(input.email).imports.size)
        assertEquals(2, books.history(input.email, h).size)
    }

    @Test fun pdfSnapshotAdapterRetainsSourceDigestAndOwnerReviewInsideSqlcipher() {
        val replayContext = sandbox()
        val (accounts, books) = create(replayContext)
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic source matching", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        // Synthetic snapshot only: exercises transactional PDF-source contract,
        // NOT the actual Chaquopy/PDF parser. Never claim CAS parser passed.
        val pdf = ("%PDF-1.4\n" + "synthetic-nonfinancial-replay-container".repeat(2)).toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(pdf)
            .joinToString("") { "%02x".format(it) }
        val source = NativeStatementCsv.Snapshot(
            sha, pdf.size, "ABCDE1234F", "2025-03-31",
            listOf(NativeStatementCsv.Position("ABCDE1234F", "2025-03-31",
                "INE000000001", "", 150_000_000L, 11_000L, 16500L)))
        val importer = NativeStatementImportRepository(accounts)
        val plan = importer.inspectSnapshot(input.email, source, pdf)
        assertEquals(1, plan.matched.size)
        assertThrows(IllegalArgumentException::class.java) {
            importer.inspectSnapshot(input.email, source, pdf + byteArrayOf(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            importer.approveSnapshot(input.email, "example.pdf", pdf + byteArrayOf(1),
                source, "CAS_PDF", plan, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        val receipt = importer.approveSnapshot(input.email, "example.pdf", pdf,
            source, "CAS_PDF", plan, true)
        assertFalse(receipt.duplicate)
        assertEquals(1, receipt.adjustments)
        assertEquals("CAS_PDF", books.read(input.email).imports.single().documentKind)
        assertEquals(2, books.history(input.email, h).size)
        assertEquals("SNAPSHOT_ADJUSTMENT", books.history(input.email, h).last().kind)
        assertTrue(importer.inspectSnapshot(input.email, source, pdf).duplicate)
        // Replaying the PREVIOUS owner approval must be idempotent inside the
        // same encrypted vault, including after a fresh admitted login.
        fun counts(repo: NativeAccountRepository): List<Long> =
            repo.withUnlockedVault(input.email) { db ->
                listOf("source_document", "import_batch", "statement_position",
                    "ledger_event").map { name ->
                    db.rawQuery("SELECT COUNT(*) FROM " + name, null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                }
            }
        val beforeReplay = counts(accounts)
        assertTrue(importer.approveSnapshot(input.email, "example.pdf", pdf,
            source, "CAS_PDF", plan, true).duplicate)
        assertEquals(beforeReplay, counts(accounts))
        accounts.onBackground()
        val reopened = NativeAccountRepository(
            replayContext, gate())
        assertTrue(reopened.login(input.email, input.password))
        assertTrue(NativeStatementImportRepository(reopened).approveSnapshot(
            input.email, "example.pdf", pdf, source, "CAS_PDF", plan, true).duplicate)
        assertEquals(beforeReplay, counts(reopened))
    }

    @Test fun pdfSnapshotApprovalRefusesWrongEncryptedFamilyIdentity() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic ISIN", "EQUITY", "Other", 0L, 0L,
            "2024-01-01", false, isin = "INE000000001"))
        val pdf = "%PDF-synthetic-only-not-a-real-pdf".toByteArray()
        val sha = MessageDigest.getInstance("SHA-256").digest(pdf)
            .joinToString("") { "%02x".format(it) }
        val position = NativeStatementCsv.Position("PQRST1234F", "2025-03-31",
            "INE000000001", "", 100_000_000L, 10_000L, null)
        val proposed = NativeStatementCsv.Snapshot(sha, pdf.size, position.ownerPan,
            position.date, listOf(position))
        assertThrows(IllegalArgumentException::class.java) {
            NativeStatementImportRepository(accounts)
                .inspectSnapshot(input.email, proposed, pdf)
        }
        assertEquals(0, books.read(input.email).imports.size)
    }

    @Test fun sharedPanAndIsinAcrossTwoCustodyAccountsIsAmbiguousAndCannotImport() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val custodyA = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic DP A",
                identifierMasked = "****1101"))
        val custodyB = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic DP B",
                identifierMasked = "****2202"))
        for (account in listOf(custodyA, custodyB)) {
            books.createHolding(input.email, NativeBooksRepository.NewHolding(
                owner, account, "Duplicated synthetic ISIN", "EQUITY", "Other",
                0L, 10000L, "2024-01-01", false, isin = "INE000000001"))
        }
        val pdf = ("%PDF-1.4\\n" + "synthetic-ambiguous-account-source".repeat(2))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(pdf)
            .joinToString("") { "%02x".format(it) }
        val proposed = NativeStatementCsv.Snapshot(digest, pdf.size,
            "ABCDE1234F", "2025-03-31",
            listOf(NativeStatementCsv.Position("ABCDE1234F", "2025-03-31",
                "INE000000001", "", 100_000_000L, 10000L, null)))
        val imports = NativeStatementImportRepository(accounts)
        fun counts(): List<Long> = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event", "holding").map { name ->
                db.rawQuery("SELECT COUNT(*) FROM " + name, null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            }
        }
        val before = counts()
        assertThrows(IllegalArgumentException::class.java) {
            imports.inspectSnapshot(input.email, proposed, pdf)
        }
        assertEquals(before, counts())
        val forgedReview = NativeStatementImportRepository.Review(digest,
            "2025-03-31", emptyList(), "0".repeat(64), false)
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(input.email, "ambiguous.pdf", pdf, proposed,
                "CAS_PDF", forgedReview, true)
        }
        assertEquals(before, counts())
    }

    @Test fun distinctCustodyIsinsMatchOnlyTheirExactAccountAndReplayWithoutWrites() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val custodyA = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Custody A",
                identifierMasked = "****1101"))
        val custodyB = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Custody B",
                identifierMasked = "****2202"))
        val selected = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, custodyA, "Synthetic Selected Equity", "EQUITY", "Other",
            100_000_000L, 10000L, "2024-01-01", true, isin = "INE000000001"))
        val untouched = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, custodyB, "Synthetic Unrelated Equity", "EQUITY", "Other",
            200_000_000L, 10000L, "2024-01-01", true, isin = "INE000000002"))
        val pdf = ("%PDF-1.4\\n" + "synthetic-exact-account-only".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(pdf)
            .joinToString("") { "%02x".format(it) }
        val statement = NativeStatementCsv.Snapshot(digest, pdf.size,
            "ABCDE1234F", "2025-03-31",
            listOf(NativeStatementCsv.Position("ABCDE1234F", "2025-03-31",
                "INE000000001", "", 150_000_000L, 11000L, null)))
        val imports = NativeStatementImportRepository(accounts)
        val reviewed = imports.inspectSnapshot(input.email, statement, pdf)
        assertEquals(1, reviewed.matched.size)
        assertEquals(selected, reviewed.matched.single().holdingId)
        assertEquals(custodyA, reviewed.matched.single().accountId)
        assertEquals(NativeStatementImportRepository.Action.APPLY_SNAPSHOT,
            reviewed.matched.single().action)
        val unrelatedBefore = books.history(input.email, untouched).size
        fun financialCounts(): List<Long> =
            accounts.withUnlockedVault(input.email) { db ->
                listOf("source_document", "import_batch", "statement_position",
                    "ledger_event", "holding").map { table ->
                    db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        cursor.getLong(0)
                    }
                }
            }
        val beforeConsent = financialCounts()
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(input.email, "synthetic.pdf", pdf,
                statement, "CAS_PDF", reviewed, false)
        }
        assertEquals(beforeConsent, financialCounts())
        assertEquals(100_000_000L,
            books.read(input.email).holdings.single { it.id == selected }.quantityScaled)
        val receipt = imports.approveSnapshot(input.email, "synthetic.pdf", pdf,
            statement, "CAS_PDF", reviewed, true)
        assertFalse(receipt.duplicate)
        assertEquals(1, receipt.positions)
        assertEquals(1, receipt.adjustments)
        assertEquals(150_000_000L,
            books.read(input.email).holdings.single { it.id == selected }.quantityScaled)
        assertEquals(200_000_000L,
            books.read(input.email).holdings.single { it.id == untouched }.quantityScaled)
        assertEquals(unrelatedBefore, books.history(input.email, untouched).size)
        // Record provenance is written once to encrypted SQLCipher tables;
        // PDF contents and the attested owner PAN are never stored here.
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT document_kind,sha256,byte_length FROM source_document",
                null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CAS_PDF", cursor.getString(0))
                assertEquals(digest, cursor.getString(1))
                assertEquals(pdf.size.toLong(), cursor.getLong(2))
                assertFalse(cursor.moveToNext())
            }
            db.rawQuery("SELECT document_kind,sha256,statement_date FROM import_batch",
                null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("CAS_PDF", cursor.getString(0))
                assertEquals(digest, cursor.getString(1))
                assertEquals("2025-03-31", cursor.getString(2))
                assertFalse(cursor.moveToNext())
            }
        }
        val committedCounts = financialCounts()
        assertTrue(imports.approveSnapshot(input.email, "synthetic.pdf", pdf,
            statement, "CAS_PDF", reviewed, true).duplicate)
        assertEquals(committedCounts, financialCounts())
        assertEquals(150_000_000L,
            books.read(input.email).holdings.single { it.id == selected }.quantityScaled)
        assertEquals(unrelatedBefore, books.history(input.email, untouched).size)
    }

    @Test fun casPdfApprovalRefusesChangedCustodyAfterReviewedPreview() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val firstAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Old DP",
                identifierMasked = "****1001"))
        val secondAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic New DP",
                identifierMasked = "****2002"))
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, firstAccount, "Synthetic CAS Custody", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true, isin = "INE000000001"))
        val sourceBytes = ("%PDF-1.4\\n" + "artificial-stale-custody-proof".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(sourceBytes)
            .joinToString("") { "%02x".format(it) }
        val proposed = NativeStatementCsv.Snapshot(digest, sourceBytes.size,
            "ABCDE1234F", "2025-03-31",
            listOf(NativeStatementCsv.Position("ABCDE1234F", "2025-03-31",
                "INE000000001", "", 150_000_000L, 11000L, null)))
        val importer = NativeStatementImportRepository(accounts)
        val reviewed = importer.inspectSnapshot(input.email, proposed, sourceBytes)
        assertEquals(firstAccount, reviewed.matched.single().accountId)
        simulateLegacyCustodyDrift(accounts, books, holding, secondAccount)
        fun counts(): List<Long> = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event", "holding").map { name ->
                db.rawQuery("SELECT COUNT(*) FROM " + name, null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            }
        }
        val before = counts()
        assertThrows(IllegalArgumentException::class.java) {
            importer.approveSnapshot(input.email, "synthetic.pdf", sourceBytes,
                proposed, "CAS_PDF", reviewed, true)
        }
        assertEquals(before, counts())
        assertEquals(secondAccount,
            books.read(input.email).holdings.single { it.id == holding }.accountId)
        assertEquals(100_000_000L,
            books.read(input.email).holdings.single { it.id == holding }.quantityScaled)
        assertEquals(0, books.read(input.email).imports.size)
    }

    @Test fun twoDistinctCustodiesCommitExactPositionsAtomicallyAndReplayOnce() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val a = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Artificial Custody One",
                identifierMasked = "****1101"))
        val b = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Artificial Custody Two",
                identifierMasked = "****2202"))
        val first = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, a, "Artificial Equity One", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true, isin = "INE000000001"))
        val second = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, b, "Artificial Equity Two", "EQUITY", "Other",
            200_000_000L, 10_000L, "2024-01-01", true, isin = "INE000000002"))
        val bytes = ("%PDF-1.4\\n" + "artificial-two-position-source".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val date = "2025-03-31"
        val source = NativeStatementCsv.Snapshot(digest, bytes.size, "ABCDE1234F",
            date, listOf(
                NativeStatementCsv.Position("ABCDE1234F", date, "INE000000001",
                    "", 150_000_000L, 11_000L, null),
                NativeStatementCsv.Position("ABCDE1234F", date, "INE000000002",
                    "", 250_000_000L, 11_000L, null)))
        val importer = NativeStatementImportRepository(accounts)
        val review = importer.inspectSnapshot(input.email, source, bytes)
        assertEquals(setOf(first, second), review.matched.map { it.holdingId }.toSet())
        assertEquals(setOf(a, b), review.matched.map { it.accountId }.toSet())
        val receipt = importer.approveSnapshot(input.email, "artificial.pdf", bytes,
            source, "CAS_PDF", review, true)
        assertFalse(receipt.duplicate)
        assertEquals(2, receipt.positions)
        assertEquals(2, receipt.adjustments)
        val positions = books.read(input.email).holdings.associateBy { it.id }
        assertEquals(150_000_000L, positions.getValue(first).quantityScaled)
        assertEquals(250_000_000L, positions.getValue(second).quantityScaled)
        fun counts(): List<Long> = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event").map { table ->
                db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cur ->
                    assertTrue(cur.moveToFirst())
                    cur.getLong(0)
                }
            }
        }
        val committedCounts = counts()
        assertTrue(importer.approveSnapshot(input.email, "artificial.pdf", bytes,
            source, "CAS_PDF", review, true).duplicate)
        assertEquals(committedCounts, counts())
    }

    @Test fun issuerCustodyBoundSameIsinCommitsBothAndRejectsReplay() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val firstAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Depository One",
                identifierMasked = "****1001", dpId = "DP000001", clientId = "CLIENT01"))
        val secondAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Depository Two",
                identifierMasked = "****2002", dpId = "DP000002", clientId = "CLIENT02"))
        val h1 = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, firstAccount, "Synthetic Same ISIN One", "EQUITY", "Other",
            100_000_000L, 10000L, "2024-01-01", true, isin = "INE000000001"))
        val h2 = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, secondAccount, "Synthetic Same ISIN Two", "EQUITY", "Other",
            200_000_000L, 10000L, "2024-01-01", true, isin = "INE000000001"))
        val bytes = ("%PDF-1.4\\n" + "synthetic-custody-provenance-only".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val whenDated = "2025-03-31"
        val firstSource = NativeStatementCsv.DematAccount("CDSL", "DP000001", "CLIENT01")
        val secondSource = NativeStatementCsv.DematAccount("CDSL", "DP000002", "CLIENT02")
        val positions = listOf(
            NativeStatementCsv.Position("ABCDE1234F", whenDated, "INE000000001",
                "", 150_000_000L, 11000L, null, firstSource),
            NativeStatementCsv.Position("ABCDE1234F", whenDated, "INE000000001",
                "", 250_000_000L, 11000L, null, secondSource))
        val snapshot = NativeStatementCsv.Snapshot(digest, bytes.size,
            "ABCDE1234F", whenDated, positions, "CDSL")
        val importer = NativeStatementImportRepository(accounts)
        val review = importer.inspectSnapshot(input.email, snapshot, bytes)
        assertEquals(listOf(firstAccount, secondAccount),
            review.matched.map { it.accountId })
        assertEquals(listOf(h1, h2), review.matched.map { it.holdingId })
        val receipt = importer.approveSnapshot(input.email, "synthetic.pdf", bytes,
            snapshot, "CAS_PDF", review, true)
        assertFalse(receipt.duplicate)
        assertEquals(2, receipt.positions)
        assertEquals(2, receipt.adjustments)
        val latest = books.read(input.email).holdings.associateBy { it.id }
        assertEquals(150_000_000L, latest.getValue(h1).quantityScaled)
        assertEquals(250_000_000L, latest.getValue(h2).quantityScaled)
        fun financialCounts() = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event").map { table ->
                db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { c ->
                    assertTrue(c.moveToFirst())
                    c.getLong(0)
                }
            }
        }
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT source_section FROM statement_position " +
                "WHERE import_batch_id=? ORDER BY holding_id",
                arrayOf(requireNotNull(receipt.batchId))).use { c ->
                val sourceRows = mutableSetOf<String>()
                while (c.moveToNext()) sourceRows.add(c.getString(0))
                assertEquals(setOf("CAS_CUSTODY|CDSL|DP000001|CLIENT01",
                    "CAS_CUSTODY|CDSL|DP000002|CLIENT02"), sourceRows)
            }
        }
        val committed = financialCounts()
        assertTrue(importer.approveSnapshot(input.email, "synthetic.pdf", bytes,
            snapshot, "CAS_PDF", review, true).duplicate)
        assertEquals(committed, financialCounts())
    }

    @Test fun mixedDepositoryAndFundFolioSnapshotCommitsAtomicallyWithReplayGuard() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val demat = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Artificial DP",
                dpId = "DP000001", clientId = "CLIENT01"))
        val fundAccount = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "FOLIO", "Artificial Fund"))
        val stock = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, demat, "Artificial Shares", "EQUITY", "Other",
            100_000_000L, 10000L, "2024-01-01", true,
            isin = "INE000000001"))
        val mf = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, fundAccount, "Artificial MF Units", "MUTUAL_FUND", "Index / ETF",
            200_000_000L, 10000L, "2024-01-01", true,
            isin = "INF000000001", folioForStatements = "DEMO-FOLIO-01"))
        val bytes = ("%PDF-1.4\\n" + "entirely-fictitious-mixed-statement".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val date = "2025-03-31"
        val source = NativeStatementCsv.Snapshot(digest, bytes.size,
            "ABCDE1234F", date, listOf(
                NativeStatementCsv.Position("ABCDE1234F", date, "INE000000001",
                    "", 150_000_000L, 11000L, null,
                    NativeStatementCsv.DematAccount("CDSL", "DP000001", "CLIENT01")),
                NativeStatementCsv.Position("ABCDE1234F", date, "INF000000001",
                    "DEMO-FOLIO-01", 250_000_000L, 12000L, null,
                    consolidatedFundFolio = true)), "CDSL")
        val imports = NativeStatementImportRepository(accounts)
        val review = imports.inspectSnapshot(input.email, source, bytes)
        assertEquals(listOf(stock, mf), review.matched.map { it.holdingId })
        assertEquals(listOf(demat, fundAccount), review.matched.map { it.accountId })
        assertTrue(review.matched[1].consolidatedFundFolio)
        val receipt = imports.approveSnapshot(input.email, "artificial.pdf", bytes,
            source, "CAS_PDF", review, true)
        assertFalse(receipt.duplicate)
        assertEquals(2, receipt.positions)
        assertEquals(2, receipt.adjustments)
        val after = books.read(input.email).holdings.associateBy { it.id }
        assertEquals(150_000_000L, after.getValue(stock).quantityScaled)
        assertEquals(250_000_000L, after.getValue(mf).quantityScaled)
        fun counts(): List<Long> = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event").map { table ->
                db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { c ->
                    assertTrue(c.moveToFirst())
                    c.getLong(0)
                }
            }
        }
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT source_section FROM statement_position " +
                "WHERE import_batch_id=?",
                arrayOf(requireNotNull(receipt.batchId))).use { cursor ->
                val provenance = mutableSetOf<String>()
                while (cursor.moveToNext()) provenance.add(cursor.getString(0))
                assertEquals(setOf("CAS_CUSTODY|CDSL|DP000001|CLIENT01",
                    "CAS_FOLIO|CDSL"), provenance)
            }
        }
        val committed = counts()
        assertTrue(imports.approveSnapshot(input.email, "artificial.pdf", bytes,
            source, "CAS_PDF", review, true).duplicate)
        assertEquals(committed, counts())
    }

    @Test fun issuerCustodyChangeAfterReviewCannotCommit() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val account = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Depository",
                dpId = "DP000001", clientId = "CLIENT01"))
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, account, "Synthetic Equity", "EQUITY", "Other",
            100_000_000L, 10000L, "2024-01-01", true,
            isin = "INE000000001"))
        val bytes = ("%PDF-1.4\\n" + "synthetic-custody-change".repeat(4))
            .toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val date = "2025-03-31"
        val snapshot = NativeStatementCsv.Snapshot(digest, bytes.size,
            "ABCDE1234F", date, listOf(
                NativeStatementCsv.Position("ABCDE1234F", date, "INE000000001",
                    "", 150_000_000L, 11000L, null,
                    NativeStatementCsv.DematAccount("CDSL", "DP000001", "CLIENT01"))),
            "CDSL")
        val imports = NativeStatementImportRepository(accounts)
        val review = imports.inspectSnapshot(input.email, snapshot, bytes)
        books.updateCustody(input.email, account,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic Depository",
                dpId = "DP000001", clientId = "CLIENT99"))
        val before = books.read(input.email)
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(input.email, "synthetic.pdf", bytes,
                snapshot, "CAS_PDF", review, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        assertEquals(before.holdings.single().quantityScaled,
            books.read(input.email).holdings.single().quantityScaled)
    }

    @Test fun changedCustodyMappingAfterPreviewCannotCommitStaleStatement() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holdingId = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic portfolio security", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        val bytes = csvSnapshot()
        val reviewed = importer.inspect(input.email, bytes)
        val newCustody = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Owned demat"))
        simulateLegacyCustodyDrift(accounts, books, holdingId, newCustody)
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "snapshot.csv", bytes, reviewed, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        assertEquals(1, books.history(input.email, holdingId).size)
    }

    @Test fun pdfAdapterStaleCustodyReviewRefusesApprovedSqlcipherWrites() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val originalCustody = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic custody A"))
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, originalCustody, "Synthetic source equity", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val sourceBytes = ("%PDF-1.4\\n" + "artificial-stale-review-only".repeat(3))
            .toByteArray(Charsets.UTF_8)
        val sourceSha = MessageDigest.getInstance("SHA-256").digest(sourceBytes)
            .joinToString("") { "%02x".format(it) }
        val snapshot = NativeStatementCsv.Snapshot(sourceSha, sourceBytes.size,
            "ABCDE1234F", "2025-03-31",
            listOf(NativeStatementCsv.Position("ABCDE1234F", "2025-03-31",
                "INE000000001", "", 150_000_000L, 11_000L, null)))
        val imports = NativeStatementImportRepository(accounts)
        val reviewed = imports.inspectSnapshot(input.email, snapshot, sourceBytes)
        assertEquals(originalCustody, reviewed.matched.single().accountId)
        val replacementCustody = books.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner, "DEMAT", "Synthetic custody B"))
        simulateLegacyCustodyDrift(accounts, books, holding, replacementCustody)
        fun counts(): List<Long> = accounts.withUnlockedVault(input.email) { db ->
            listOf("source_document", "import_batch", "statement_position",
                "ledger_event").map { table ->
                db.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getLong(0)
                }
            }
        }
        val before = counts()
        assertThrows(IllegalArgumentException::class.java) {
            imports.approveSnapshot(input.email, "synthetic.pdf", sourceBytes,
                snapshot, "CAS_PDF", reviewed, true)
        }
        assertEquals(before, counts())
        assertEquals(100_000_000L,
            books.read(input.email).holdings.single().quantityScaled)
        assertEquals(replacementCustody,
            books.read(input.email).holdings.single().accountId)
    }

    @Test fun unsafeLedgerHistoryCannotBeHealedByImportingAReducedSnapshot() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Original source of units", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        // Valid, explicitly recorded sale first, so the source snapshot can
        // reduce only the surviving quantity. A negative signed difference
        // must never appear as a taxable SELL.
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "SELL", "2025-01-31", 50_000_000L, 10_000L, 5_000L))
        val pdfBytes = csvSnapshot(qty = 25_000_000L)
        val plan = importer.inspect(input.email, pdfBytes)
        assertEquals(-25_000_000L, plan.matched.single().delta)
        importer.approve(input.email, "ledger-snapshot.csv", pdfBytes, plan, true)
        val events = books.history(input.email, holding)
        assertEquals(3, events.size)
        assertEquals("SNAPSHOT_ADJUSTMENT", events.last().kind)
        assertEquals(25_000_000L, books.read(input.email).holdings.single().quantityScaled)
    }

    @Test fun recordedMoneyWeightedReturnDoesNotCountSyntheticStatementOpening() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val withoutBuys = books.createHolding(input.email,
            NativeBooksRepository.NewHolding(owner, null,
                "Statement only holding", "EQUITY", "Other",
                100_000_000L, 10_000L, "2024-01-01", true))
        assertNull(books.moneyWeightedReturn(input.email, withoutBuys, "2025-01-01"))

        val investing = books.createHolding(input.email,
            NativeBooksRepository.NewHolding(owner, null,
                "Transaction based holding", "EQUITY", "Other",
                0L, 10_000L, "2024-01-01", true))
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            investing, "BUY", "2024-01-01",
            100_000_000L, 10_000L, 10_000L))
        books.recordManualPrice(input.email, investing, 11_000L, "2025-01-01")
        val expected = books.moneyWeightedReturn(input.email, investing, "2025-01-01")
        assertNotNull(expected)
        assertTrue(expected!! > 9.0 && expected < 11.0)
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        assertEquals(expected,
            NativeBooksRepository(reopened).moneyWeightedReturn(
                input.email, investing, "2025-01-01"))
    }

    @Test fun unchangedQuantityButNewDividendInvalidatesReviewedImport() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic dividend security", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val bytes = csvSnapshot(qty = 150_000_000L)
        val importer = NativeStatementImportRepository(accounts)
        val initial = importer.inspect(input.email, bytes)
        val expectedQuantity = books.read(input.email).holdings.single().quantityScaled
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            holding, "DIVIDEND", "2025-02-01", 0L, 0L, 500L))
        assertEquals(expectedQuantity, books.read(input.email).holdings.single().quantityScaled)
        val after = importer.inspect(input.email, bytes)
        assertNotEquals(initial.decisionSha256, after.decisionSha256)
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "reviewed-before-dividend.csv",
                bytes, initial, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        assertEquals(2, books.history(input.email, holding).size)
        val receipt = importer.approve(input.email, "reviewed-after-dividend.csv",
            bytes, after, true)
        assertEquals(1, receipt.adjustments)
    }

    @Test fun changedRecordedPriceWithoutUnitChangeInvalidatesStatementApproval() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic price security", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        val bytes = csvSnapshot(qty = 150_000_000L)
        val reviewed = importer.inspect(input.email, bytes)
        books.recordManualPrice(input.email, holding, 16_000L, "2025-04-01")
        assertNotEquals(reviewed.decisionSha256,
            importer.inspect(input.email, bytes).decisionSha256)
        assertThrows(IllegalArgumentException::class.java) {
            importer.approve(input.email, "old-review.csv", bytes, reviewed, true)
        }
        assertEquals(0, books.read(input.email).imports.size)
        assertEquals("USER_OVERRIDE", books.read(input.email).holdings.single().priceSource)
    }

    @Test fun olderManualPriceIsAuditedWithoutRewindingNewerValuation() {
        val ctx = sandbox()
        val (accounts, books) = create(ctx)
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic dated market price", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true))
        books.recordManualPrice(input.email, h, 20_000L, "2025-05-31")
        books.recordManualPrice(input.email, h, 16_000L, "2025-03-31")
        val current = books.read(input.email).holdings.single()
        assertEquals(20_000L, current.marketPriceMinor)
        assertEquals("USER_OVERRIDE", current.priceSource)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT price_date,price_minor,source FROM price_observation " +
                "ORDER BY price_date", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2025-03-31", c.getString(0))
                assertEquals(16_000L, c.getLong(1))
                assertEquals("USER_OVERRIDE", c.getString(2))
                assertTrue(c.moveToNext())
                assertEquals("2025-05-31", c.getString(0))
                assertEquals(20_000L, c.getLong(1))
                assertFalse(c.moveToNext())
            }
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(ctx, gate())
        assertTrue(reopened.login(input.email, input.password))
        assertEquals(20_000L,
            NativeBooksRepository(reopened).read(input.email)
                .holdings.single().marketPriceMinor)
    }

    @Test fun importedCasPriceCannotOverrideOwnersSameDayManualPrice() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic reviewed CAS NAV", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        books.recordManualPrice(input.email, h, 18_000L, "2025-03-31")
        val importer = NativeStatementImportRepository(accounts)
        val bytes = csvSnapshot(qty = 100_000_000L)
        val review = importer.inspect(input.email, bytes)
        val receipt = importer.approve(input.email, "synthetic-cas.csv", bytes,
            review, true)
        assertEquals(0, receipt.adjustments)
        assertEquals(18_000L, books.read(input.email).holdings.single().marketPriceMinor)
        assertEquals("USER_OVERRIDE",
            books.read(input.email).holdings.single().priceSource)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT source,price_minor FROM price_observation " +
                "ORDER BY source", null).use { c ->
                val observations = mutableMapOf<String, Long>()
                while (c.moveToNext()) observations[c.getString(0)] = c.getLong(1)
                assertEquals(18_000L, observations["USER_OVERRIDE"])
                assertEquals(12_500L, observations["CSV_STATEMENT"])
            }
        }
    }

    @Test fun earlierCasStatementAddsHistoricalPriceWithoutRewindingLaterOne() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic chronological instrument", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val importer = NativeStatementImportRepository(accounts)
        val latest = csvSnapshot(date = "2025-05-31", qty = 150_000_000L)
        importer.approve(input.email, "later.csv", latest,
            importer.inspect(input.email, latest), true)
        assertEquals("CSV_STATEMENT",
            books.read(input.email).holdings.single().priceSource)
        val prior = csvSnapshot(date = "2025-03-31", qty = 125_000_000L)
        val reviewed = importer.inspect(input.email, prior)
        assertEquals(NativeStatementImportRepository.Action.HISTORICAL_ONLY,
            reviewed.matched.single().action)
        importer.approve(input.email, "earlier.csv", prior, reviewed, true)
        val current = books.read(input.email).holdings.single()
        assertEquals(12_500L, current.marketPriceMinor)
        assertEquals(150_000_000L, current.quantityScaled)
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT price_date FROM price_observation " +
                "ORDER BY price_date", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("2025-03-31", c.getString(0))
                assertTrue(c.moveToNext())
                assertEquals("2025-05-31", c.getString(0))
                assertFalse(c.moveToNext())
            }
        }
    }

    @Test fun futureOrMalformedManualPriceCannotWriteAnyObservation() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic invalid price", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true))
        for (badDay in listOf("2099-01-01", "2025-2-1", "not-a-date")) {
            assertThrows(Exception::class.java) {
                books.recordManualPrice(input.email, h, 14_000L, badDay)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            books.recordManualPrice(input.email, h, -1L, "2025-02-01")
        }
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT COUNT(*) FROM price_observation", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(0L, c.getLong(0))
            }
        }
    }

    @Test fun historicalXirrNeverLooksAheadToFuturePriceOrFutureTransaction() {
        val (_, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val h = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic historic XIRR", "EQUITY", "Other",
            0L, 10_000L, "2024-01-01", true))
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            h, "BUY", "2024-01-01", 100_000_000L, 10_000L, 10_000L))
        books.recordManualPrice(input.email, h, 20_000L, "2025-06-30")
        assertNull(books.moneyWeightedReturn(input.email, h, "2025-01-01"))
        // A later price cannot erase the earlier dated observation.
        books.recordManualPrice(input.email, h, 11_000L, "2025-01-01")
        val historical = books.moneyWeightedReturn(input.email, h, "2025-01-01")
        assertNotNull(historical)
        assertTrue(historical!! in 9.0..11.0)
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            h, "BUY", "2025-03-01", 100_000_000L, 10_000L, 10_000L))
        val stable = books.moneyWeightedReturn(input.email, h, "2025-01-01")
        assertEquals(historical, stable)
        assertEquals(20_000L, books.read(input.email).holdings.single().marketPriceMinor)
        assertThrows(IllegalArgumentException::class.java) {
            books.moneyWeightedReturn(input.email, h, "2099-01-01")
        }
    }

    @Test fun sharedInstrumentCannotLeakAnotherHoldingsManualPriceIntoXirr() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val first = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "First synthetic custody", "EQUITY", "Other",
            0L, 10_000L, "2024-01-01", true))
        val second = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Second synthetic custody", "EQUITY", "Other",
            0L, 10_000L, "2024-01-01", true))
        // Historical/merged native files may deliberately reuse an instrument
        // across two owner-specific custody holdings.
        accounts.withUnlockedVault(input.email) { db ->
            db.execSQL("UPDATE holding SET instrument_id=" +
                "(SELECT instrument_id FROM holding WHERE holding_id=?) " +
                "WHERE holding_id=?", arrayOf<Any>(first, second))
        }
        books.appendUserEvent(input.email, NativeBooksRepository.NewEvent(
            second, "BUY", "2024-01-01", 100_000_000L, 10_000L, 10_000L))
        books.recordManualPrice(input.email, first, 20_000L, "2025-01-01")
        assertNull(books.moneyWeightedReturn(input.email, second, "2025-01-01"))
    }

    @Test fun officialAmfiNavPersistsOnlyMatchingMutualFundAndRetainsManualOverride() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val fund = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic NAV holding", "MUTUAL_FUND", "Equity fund",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INF000000001"))
        val feed = ("Scheme Code;ISIN Growth;ISIN Reinvestment;Scheme Name;" +
            "Net Asset Value;Date\n" +
            "101101;INF000000001;;Synthetic mutual fund;12.3456;19-Oct-2026\n")
            .toByteArray(Charsets.UTF_8)
        val parsed = NativeAmfiNavCatalog.parse(feed,
            java.time.LocalDate.parse("2026-10-20"))
        val receipt = NativeAmfiNavRepository(accounts)
            .applyVerifiedCatalog(input.email, parsed)
        assertEquals(1, receipt.matched)
        assertEquals(1, receipt.displayedPriceChanged)
        assertEquals("AMFI_OFFICIAL", books.read(input.email).holdings.single().priceSource)
        assertEquals(123_456L, books.read(input.email).holdings.single().marketPriceMinor)
        books.recordManualPrice(input.email, fund, 130_000L, "2026-10-19")
        val second = NativeAmfiNavRepository(accounts)
            .applyVerifiedCatalog(input.email, parsed)
        assertEquals(0, second.displayedPriceChanged)
        val saved = books.read(input.email).holdings.single()
        assertEquals(130_000L, saved.marketPriceMinor)
        assertEquals("USER_OVERRIDE", saved.priceSource)
    }

    @Test fun gmailJournalWaitsForOwnerApprovedCasAndNeverAdvancesMissingCursor() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Synthetic Gmail holding", "EQUITY", "Other",
            100_000_000L, 10_000L, "2024-01-01", true,
            isin = "INE000000001"))
        val journal = NativeGmailSyncLedger(accounts)
        val bytes = csvSnapshot()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val message = NativeGmailSyncLedger.MessageKey(
            "one@example.invalid", "message123", "attach456", sha)
        assertThrows(IllegalArgumentException::class.java) {
            journal.alreadySeen(input.email, owner, message)
        }
        journal.setMemberOptIn(input.email, owner, true)
        assertThrows(IllegalArgumentException::class.java) {
            journal.alreadySeen(input.email, owner, message)
        }
        // Explicit encrypted account-to-member mapping is required independently
        // of consent. Do not weaken NativeGmailSyncLedger's fail-closed guard.
        journal.bindGmailAccount(input.email, owner, message.gmailAddress)
        assertFalse(journal.alreadySeen(input.email, owner, message))
        assertThrows(IllegalArgumentException::class.java) {
            journal.markReconciled(input.email, owner, message)
        }
        assertThrows(IllegalArgumentException::class.java) {
            journal.advanceCompleteCursor(input.email, owner,
                "one@example.invalid", "1000", true, listOf(message))
        }
        assertNull(journal.currentCursor(input.email, owner, "one@example.invalid"))
        val importer = NativeStatementImportRepository(accounts)
        val review = importer.inspect(input.email, bytes)
        importer.approve(input.email, "synthetic.csv", bytes, review, true)
        assertTrue(journal.markReconciled(input.email, owner, message).added)
        assertFalse(journal.markReconciled(input.email, owner, message).added)
        journal.advanceCompleteCursor(input.email, owner,
            "one@example.invalid", "1000", true, listOf(message))
        assertEquals("1000", journal.currentCursor(
            input.email, owner, "one@example.invalid"))
        assertThrows(IllegalArgumentException::class.java) {
            journal.advanceCompleteCursor(input.email, owner,
                "one@example.invalid", "999", true, listOf(message))
        }
        val otherAccountMessage = message.copy(gmailAddress = "two@example.invalid")
        assertThrows(IllegalArgumentException::class.java) {
            journal.alreadySeen(input.email, owner, otherAccountMessage)
        }
        journal.bindGmailAccount(input.email, owner, otherAccountMessage.gmailAddress)
        assertFalse(journal.alreadySeen(input.email, owner, otherAccountMessage))
        assertTrue(journal.alreadySeen(input.email, owner, message))
        journal.setMemberOptIn(input.email, owner, false)
        assertThrows(IllegalArgumentException::class.java) {
            journal.currentCursor(input.email, owner, "one@example.invalid")
        }
    }

    @Test fun annualCreditIsAtomicIdempotentAndSurvivesEncryptedReopen() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Family PPF", "FIXED_INCOME", "PPF",
            100_000_000L, 1_000_000L, "2024-01-01", true,
            interestRateBps = 710))
        val repo = NativeAnnualInterestCreditRepository(accounts)
        val original = NativeAnnualInterestCredit.Input("PPF",
            LocalDate.parse("2026-03-31"), 1_000_000L, 710,
            LocalDate.parse("2024-01-01"))
        val checked = repo.inspect(input.email, holding, original)
        assertEquals(71_000L, checked.credit.amountMinor)
        val before = books.history(input.email, holding).size
        assertThrows(IllegalArgumentException::class.java) {
            repo.approve(input.email, checked, false)
        }
        assertEquals(before, books.history(input.email, holding).size)
        val result = repo.approve(input.email, checked, true)
        assertEquals(1_071_000L, result.newBalanceMinor)
        assertEquals(before + 1, books.history(input.email, holding).size)
        assertEquals("INTEREST", books.history(input.email, holding).last().kind)
        assertThrows(Exception::class.java) {
            repo.approve(input.email, checked, true)
        }
        assertEquals(before + 1, books.history(input.email, holding).size)
        accounts.onBackground()
        val restored = NativeAccountRepository(context, gate())
        assertTrue(restored.login(input.email, input.password))
        val persisted = NativeBooksRepository(restored).read(input.email)
        assertEquals(1_071_000L, persisted.holdings.single().marketPriceMinor)
        assertEquals(before + 1, NativeBooksRepository(restored)
            .history(input.email, holding).size)
    }

    @Test fun staleAnnualReviewOrLaterDepositCannotSilentlyAccrueInterest() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Family EPF", "FIXED_INCOME", "EPF",
            100_000_000L, 2_000_000L, "2024-01-01", true,
            interestRateBps = 650))
        val repo = NativeAnnualInterestCreditRepository(accounts)
        val term = NativeAnnualInterestCredit.Input("EPF",
            LocalDate.parse("2026-03-31"), 2_000_000L, 650,
            LocalDate.parse("2024-01-01"))
        val checked = repo.inspect(input.email, holding, term)
        books.recordManualPrice(input.email, holding, 2_050_000L, "2025-08-01")
        assertThrows(Exception::class.java) {
            repo.approve(input.email, checked, true)
        }
        assertEquals(1, books.history(input.email, holding).size)
        assertThrows(Exception::class.java) {
            repo.inspect(input.email, holding, term)
        }
    }

    @Test fun unknownOpeningCostNeverQualifiesForAnnualCompounding() {
        val (accounts, books) = create(sandbox())
        val owner = books.read(input.email).people.single().id
        val holding = books.createHolding(input.email, NativeBooksRepository.NewHolding(
            owner, null, "Unknown-cost SSY", "FIXED_INCOME",
            "Sukanya Samriddhi (SSY)", 100_000_000L, 500_000L,
            "2024-01-01", knownOpeningCost = false, interestRateBps = 820))
        val repo = NativeAnnualInterestCreditRepository(accounts)
        assertThrows(Exception::class.java) {
            repo.inspect(input.email, holding, NativeAnnualInterestCredit.Input(
                "SSY", LocalDate.parse("2026-03-31"), 500_000L, 820,
                LocalDate.parse("2024-01-01")))
        }
        assertEquals(1, books.history(input.email, holding).size)
    }


    @Test fun p36ExplicitVersionedPrivacyConsentNeedsReviewAndSurvivesEncryptedRestart() {
        val context = sandbox()
        val (accounts, _) = create(context)
        val privacy = NativePrivacyConsentRepository(accounts)
        val feedback = NativeFeedbackStateRepository(accounts)
        val day = 86_400_000L
        feedback.recordAuthenticatedOpen(input.email, 10 * day)
        feedback.finishOnboarding(input.email, 11 * day)
        assertNull(privacy.accepted(input.email))
        val invalid = NativePrivacyConsentRepository.Review(
            NativePrivacyNotice.TEXT_ID, false, true, 12 * day)
        assertThrows(IllegalArgumentException::class.java) {
            privacy.accept(input.email, invalid)
        }
        assertThrows(IllegalArgumentException::class.java) {
            privacy.accept(input.email, invalid.copy(reachedEnd = true,
                textId = "older-notice"))
        }
        assertFalse(feedback.eligible(input.email, 17 * day, NativePrivacyNotice.VERSION))
        val accepted = privacy.accept(input.email, invalid.copy(
            reachedEnd = true, acceptedAtMillis = 12 * day))
        assertEquals(NativePrivacyNotice.VERSION, accepted.version)
        assertTrue(feedback.eligible(input.email, 17 * day, NativePrivacyNotice.VERSION))
        assertFalse(feedback.eligible(input.email, 17 * day, "another-version"))
        assertThrows(IllegalArgumentException::class.java) {
            privacy.accept(input.email, invalid.copy(reachedEnd = true,
                acceptedAtMillis = 11 * day))
        }
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val restoredPrivacy = NativePrivacyConsentRepository(reopened)
        assertEquals(accepted, restoredPrivacy.accepted(input.email))
        assertTrue(NativeFeedbackStateRepository(reopened).eligible(
            input.email, 17 * day, NativePrivacyNotice.VERSION))
    }

    @Test fun p36FeedbackTimingIsEncryptedRestartSafeAndMissingPrivacyConsentFailsClosed() {
        val context = sandbox()
        val (accounts, _) = create(context)
        val feedback = NativeFeedbackStateRepository(accounts)
        val day = 86_400_000L
        val first = feedback.recordAuthenticatedOpen(input.email, 10 * day)
        assertEquals(10 * day, first.firstSeenMillis)
        assertFalse(feedback.eligible(input.email, 17 * day, "privacy-v1"))
        feedback.finishOnboarding(input.email, 11 * day)
        assertFalse(feedback.eligible(input.email, 17 * day, "privacy-v1"))
        val snoozed = feedback.dismiss(input.email, 17 * day)
        assertEquals(1, snoozed.promptCount)
        assertEquals(19 * day, snoozed.snoozeUntilMillis)
        assertThrows(IllegalArgumentException::class.java) {
            feedback.recordAuthenticatedOpen(input.email, 16 * day)
        }
        accounts.onBackground()
        val restarted = NativeAccountRepository(context, gate())
        assertTrue(restarted.login(input.email, input.password))
        val again = NativeFeedbackStateRepository(restarted)
        assertEquals(snoozed, again.readRecorded(input.email))
        assertFalse(again.eligible(input.email, 19 * day, "privacy-v1"))
        assertThrows(Exception::class.java) {
            NativeFeedbackStateRepository(accounts).readRecorded(input.email)
        }
    }

    @Test fun p35EncryptedSharedMailboxLinksRemainOwnerScopedAfterRestart() {
        val context = sandbox()
        val (accounts, books) = create(context)
        val owner = books.read(input.email).people.single().id
        val member = books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Second family member", "Spouse"))
        val gmail = NativeGmailSyncLedger(accounts)
        val address = "family-inbox@example.org"
        gmail.setMemberOptIn(input.email, owner, true)
        gmail.setMemberOptIn(input.email, member, true)
        assertThrows(IllegalArgumentException::class.java) {
            gmail.requireGmailAccountOwner(input.email, owner, address)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeGmailSyncSchedule.requestManual(context, gmail, input.email, owner, address)
        }
        gmail.bindGmailAccount(input.email, owner, address)
        gmail.bindGmailAccount(input.email, member, address)
        val personalAddress = "personal-mailbox@example.org"
        gmail.bindGmailAccount(input.email, owner, personalAddress)
        gmail.requireGmailAccountOwner(input.email, owner, personalAddress)
        assertThrows(IllegalArgumentException::class.java) {
            gmail.requireGmailAccountOwner(input.email, member, personalAddress)
        }
        gmail.requireGmailAccountOwner(input.email, owner, address)
        gmail.requireGmailAccountOwner(input.email, member, address)
        assertNull(gmail.currentCursor(input.email, owner, address))
        assertNull(gmail.currentCursor(input.email, member, address))
        accounts.onBackground()
        val reopened = NativeAccountRepository(context, gate())
        assertTrue(reopened.login(input.email, input.password))
        val restored = NativeGmailSyncLedger(reopened)
        restored.requireGmailAccountOwner(input.email, owner, address)
        restored.requireGmailAccountOwner(input.email, member, address)
        restored.unlinkGmailAccount(input.email, owner, address)
        assertThrows(IllegalArgumentException::class.java) {
            restored.requireGmailAccountOwner(input.email, owner, address)
        }
        restored.requireGmailAccountOwner(input.email, member, address)
        restored.setMemberOptIn(input.email, member, false)
        assertThrows(IllegalArgumentException::class.java) {
            restored.requireGmailAccountOwner(input.email, member, address)
        }
        restored.setMemberOptIn(input.email, member, true)
        assertThrows(IllegalArgumentException::class.java) {
            restored.requireGmailAccountOwner(input.email, member, address)
        }
    }
}
