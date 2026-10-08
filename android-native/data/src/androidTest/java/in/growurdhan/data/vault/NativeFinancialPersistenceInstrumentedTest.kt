package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.finance.FinancialEvent
import `in`.growurdhan.domain.finance.FinancialEventKind
import java.io.File
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real encrypted DB and Android Keystore integration tests, never real family data. */
@RunWith(AndroidJUnit4::class)
class NativeFinancialPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var alias: String
    private lateinit var vault: SqlCipherVaultRepository
    private val owner = OwnerId("self")
    private val holdingId = HoldingId("fund")
    private val holding = Holding(
        holdingId, owner, null, "Synthetic Index Fund", "MUTUAL_FUND",
        QuantityScaled(0), MoneyScaled(250_000),
    )

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        folder = File(context.noBackupFilesDir, "m3-ledger-test-$suffix")
        alias = "in.growurdhan.m3.test.$suffix"
        vault = SqlCipherVaultRepository(context, folder, alias)
    }

    @After fun cleanup(): Unit = runBlocking {
        vault.close()
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private suspend fun seed() {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Member", "Self", true))
        vault.createHolding(holding)
    }

    private fun event(
        id: String,
        kind: FinancialEventKind,
        quantity: Long = 100_000_000L,
        price: Long = 125_000L,
        supersedes: String? = null,
        charges: Long = 0,
    ) = FinancialEvent(
        id = id,
        ownerId = owner,
        holdingId = holdingId,
        kind = kind,
        date = LocalDate.parse("2026-09-01"),
        quantityScaled = quantity,
        unitPrice = MoneyScaled(price),
        totalAmount = MoneyScaled(125_000),
        charges = MoneyScaled(charges),
        supersedesEventId = supersedes,
    )

    @Test fun encryptedV1DatabaseUpgradesWithoutLosingEvents(): Unit = runBlocking {
        assertTrue(folder.mkdirs())
        val bytes = VaultKeyEnvelope(File(folder, "wrapped.key"), alias)
            .unwrapOrCreate(allowCreate = true)
        // This fixture opens SQLCipher directly, without repository.openOrCreate(),
        // so it must load the native library itself (test ordering is not guaranteed).
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openOrCreateDatabase(
            File(folder, "wealth.db"), bytes, null, null, null,
        )
        try {
            db.execSQL("PRAGMA foreign_keys = ON")
            // The fixture uses the exact production v1 schema, not a second SQL copy.
            for (sql in VaultSchema.legacyV1Statements) db.execSQL(sql)
            db.execSQL("PRAGMA user_version = 1")
            db.execSQL(
                "INSERT INTO family_member(id,display_name,relationship,is_primary) " +
                    "VALUES('self','Original M2 Member','Self',1)"
            )
            db.execSQL(
                "INSERT INTO holding(id,owner_id,display_name,asset_category,quantity_scaled,price_scaled) " +
                    "VALUES('fund','self','Original M2 Holding','MUTUAL_FUND',0,250000)"
            )
            db.execSQL(
                "INSERT INTO ledger_event(id,owner_id,holding_id,kind,event_date," +
                    "quantity_delta_scaled,amount_scaled) " +
                    "VALUES('v1-event','self','fund','BUY','2026-03-01',100000000,125000)"
            )
        } finally {
            db.close()
            bytes.fill(0)
        }
        // Emulate a valid M2 vault's already committed ready marker.
        File(folder, "ready").writeText("GUDREADY02")
        val opened = vault.openOrCreate()
        assertEquals(2, opened.schemaVersion)
        assertEquals("Original M2 Member", vault.members().single().displayName)
        assertEquals("Original M2 Holding", vault.holdings().single().name)
        val migrated = vault.events().single()
        assertEquals("v1-event", migrated.id)
        assertEquals(LedgerKind.BUY, migrated.kind)
        assertNull(migrated.unitPrice)
        assertNull(migrated.supersedesEventId)
        assertEquals(0L, migrated.charges.raw)
        assertEquals(0, vault.health().foreignKeyViolations)
        vault.close()
        assertEquals(2, vault.openOrCreate().schemaVersion)
        assertEquals("v1-event", vault.events().single().id)
    }

    @Test fun financialKindsFeesAndCorrectionsSurviveCloseAndReopen(): Unit = runBlocking {
        seed()
        vault.appendFinancialEvents(listOf(
            event("opening", FinancialEventKind.BUY, charges = 2_500),
            event("bonus", FinancialEventKind.BONUS, price = 0L),
            event("split", FinancialEventKind.SPLIT, quantity = 2L, price = 1L),
            event("adjustment", FinancialEventKind.SNAPSHOT_ADJUSTMENT),
            event("correction", FinancialEventKind.CORRECTION, supersedes = "opening"),
        ))
        val first = vault.readBook()
        assertEquals(1, first.members.size)
        assertEquals(1, first.holdings.size)
        assertEquals(5, first.events.size)
        assertEquals(LedgerKind.BONUS, first.events.single { it.id == "bonus" }.kind)
        assertEquals(LedgerKind.SPLIT, first.events.single { it.id == "split" }.kind)
        assertEquals(LedgerKind.SNAPSHOT_ADJUSTMENT, first.events.single { it.id == "adjustment" }.kind)
        assertEquals(2_500L, first.events.single { it.id == "opening" }.charges.raw)
        assertEquals("opening", first.events.single { it.id == "correction" }.supersedesEventId)
        vault.close()
        vault.openOrCreate()
        assertEquals(first, vault.readBook())
    }

    @Test fun batchedLedgerInsertRollsBackOnMissingOrCrossOwnerSupersession(): Unit = runBlocking {
        seed()
        assertThrows(Exception::class.java) {
            runBlocking {
                vault.appendFinancialEvents(listOf(
                    event("first", FinancialEventKind.BUY),
                    event("invalid", FinancialEventKind.CORRECTION, supersedes = "not-found"),
                ))
            }
        }
        assertTrue(vault.readBook().events.isEmpty())
        vault.appendFinancialEvents(listOf(event("first", FinancialEventKind.BUY)))
        vault.createMember(FamilyMember(OwnerId("spouse"), "Synthetic Spouse", "Spouse", false))
        vault.createHolding(holding.copy(id = HoldingId("spouse-fund"), ownerId = OwnerId("spouse")))
        val otherOwner = event("cross", FinancialEventKind.CORRECTION, supersedes = "first")
            .copy(ownerId = OwnerId("spouse"), holdingId = HoldingId("spouse-fund"))
        assertThrows(Exception::class.java) {
            runBlocking { vault.appendFinancialEvents(listOf(otherOwner)) }
        }
        assertEquals(listOf("first"), vault.readBook().events.map { it.id })
    }

    @Test fun versionTwoBackupRoundTripRestoresExactRichLedger(): Unit = runBlocking {
        seed()
        vault.appendFinancialEvents(listOf(
            event("original", FinancialEventKind.BUY, charges = 15),
            event("correction", FinancialEventKind.CORRECTION, supersedes = "original"),
        ))
        val passphrase = "synthetic v2 backup password".toCharArray()
        val bytes = vault.exportEncryptedBackup(passphrase)
        val restoredRoot = File(context.noBackupFilesDir, "m3-restore-" + UUID.randomUUID())
        val restoredAlias = "in.growurdhan.m3.backup." + UUID.randomUUID()
        val restored = SqlCipherVaultRepository(context, restoredRoot, restoredAlias)
        try {
            restored.openOrCreate()
            restored.restoreEncryptedBackup(passphrase, bytes)
            assertEquals(vault.readBook(), restored.readBook())
            assertEquals(0, restored.health().foreignKeyViolations)
        } finally {
            restored.close()
            restoredRoot.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(restoredAlias)
            passphrase.fill('\u0000')
        }
    }

    @Test fun versionOnePortableBackupImportsToVersionTwoDefaults(): Unit = runBlocking {
        seed()
        val old = LedgerEvent(
            "v1", holdingId, owner, LedgerKind.BUY, "2026-01-01",
            100_000_000L, MoneyScaled(125_000), null,
        )
        vault.appendEvent(old)
        val passphrase = "synthetic legacy backup".toCharArray()
        val encrypted = vault.exportEncryptedBackup(passphrase)
        val modern = JSONObject(
            VaultBackupCodec.decrypt(encrypted, passphrase).toString(Charsets.UTF_8)
        )
        modern.put("format", 1)
        modern.put("databaseVersion", 1)
        val previous = modern.getJSONArray("ledger_event")
        val legacy = JSONArray()
        for (index in 0 until previous.length()) {
            val row = previous.getJSONArray(index)
            val older = JSONArray()
            for (column in 0 until 8) older.put(row.get(column))
            legacy.put(older)
        }
        modern.put("ledger_event", legacy)
        val priorBackup = VaultBackupCodec.encrypt(
            modern.toString().toByteArray(Charsets.UTF_8), passphrase,
        )
        val targetRoot = File(context.noBackupFilesDir, "m3-oldbackup-" + UUID.randomUUID())
        val targetAlias = "in.growurdhan.m3.oldbackup." + UUID.randomUUID()
        val target = SqlCipherVaultRepository(context, targetRoot, targetAlias)
        try {
            target.openOrCreate()
            target.restoreEncryptedBackup(passphrase, priorBackup)
            assertEquals(vault.readBook(), target.readBook())
            assertEquals(0, target.health().foreignKeyViolations)
        } finally {
            target.close()
            targetRoot.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(targetAlias)
            passphrase.fill('\u0000')
        }
    }
}
