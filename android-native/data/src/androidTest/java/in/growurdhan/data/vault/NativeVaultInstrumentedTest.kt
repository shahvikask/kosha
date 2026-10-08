package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.*
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeVaultInstrumentedTest {
    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var alias: String
    private lateinit var vault: SqlCipherVaultRepository
    private val owner = OwnerId("self")
    private val holding = Holding(
        HoldingId("h1"), owner, null, "Synthetic Fund", "MUTUAL_FUND",
        QuantityScaled(200_000_000), MoneyScaled(125_000),
    )

    @Before fun prepare() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        folder = File(context.noBackupFilesDir, "m2-vault-test-" + suffix)
        alias = "in.growurdhan.test." + suffix
        vault = SqlCipherVaultRepository(context, folder, alias)
    }

    @After fun clean() = runBlocking {
        vault.close()
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun statusDoesNotCreateAndDatabaseIsCiphertext() = runBlocking {
        assertFalse(vault.status().initialized)
        assertFalse(folder.exists())
        assertTrue(vault.openOrCreate().initialized)
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.createHolding(holding)
        vault.close()
        val data = File(folder, "wealth.db")
        val first = ByteArray(16)
        data.inputStream().use { assertEquals(16, it.read(first)) }
        assertFalse(first.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)))
        assertTrue(File(folder, "wrapped.key").exists())
        assertNull(
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey(alias, null).encoded
        )
        vault.openOrCreate()
        assertEquals(listOf(holding), vault.holdings())
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun missingWrappedKeyFailsClosedWithoutErasingDatabase() = runBlocking {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.close()
        val db = File(folder, "wealth.db")
        val checksum = MessageDigest.getInstance("SHA-256").digest(db.readBytes())
        assertTrue(File(folder, "wrapped.key").delete())
        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.openOrCreate() }
        }
        assertArrayEquals(checksum, MessageDigest.getInstance("SHA-256").digest(db.readBytes()))
    }

    @Test fun missingDatabaseWithSurvivingKeyCannotBeRecreated() = runBlocking {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.close()
        val key = File(folder, "wrapped.key")
        assertTrue(key.exists())
        val file = File(folder, "wealth.db")
        assertTrue(file.delete())
        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.status() }
        }
        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.openOrCreate() }
        }
        assertFalse(file.exists())
        assertTrue(key.exists())
    }

    @Test fun olderSnapshotIsRejectedWithoutChangingNewerBook() = runBlocking {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.createHolding(holding)
        val position = StatementPosition(holding.id, QuantityScaled(100_000_000), MoneyScaled(125_000))
        val newest = StatementSnapshot(
            "latest", owner, "b".repeat(64), "2026-10-08", true, listOf(position),
        )
        assertEquals(SnapshotResult.IMPORTED, vault.importSnapshot(newest))
        val older = newest.copy(id = "older", sha256 = "c".repeat(64), asOf = "2026-09-30")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.importSnapshot(older) }
        }
        assertEquals(SnapshotResult.ALREADY_IMPORTED, vault.importSnapshot(newest))
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun crossOwnerSnapshotFailureRollsBackAndHashIsIdempotent() = runBlocking {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.createMember(FamilyMember(OwnerId("spouse"), "Synthetic Spouse", "Spouse", false))
        vault.createHolding(holding)
        vault.createHolding(holding.copy(id = HoldingId("h2"), ownerId = OwnerId("spouse")))
        val position = StatementPosition(holding.id, QuantityScaled(100_000_000), MoneyScaled(125_000))
        val snapshot = StatementSnapshot(
            "snapshot-1", owner, "a".repeat(64), "2026-10-08", true,
            listOf(position, position.copy(holdingId = HoldingId("h2"))),
        )
        assertThrows(Exception::class.java) {
            runBlocking { vault.importSnapshot(snapshot) }
        }
        val valid = snapshot.copy(positions = listOf(position))
        assertEquals(SnapshotResult.IMPORTED, vault.importSnapshot(valid))
        assertEquals(SnapshotResult.ALREADY_IMPORTED, vault.importSnapshot(valid))
        assertTrue(vault.events().isEmpty())
        assertEquals(0, vault.health().foreignKeyViolations)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.importSnapshot(valid.copy(complete = false)) }
        }
    }

    @Test fun directSqlCannotUpdateOrDeleteLedger() {
        System.loadLibrary("sqlcipher")
        val path = File(context.noBackupFilesDir, "m2-trigger-" + UUID.randomUUID() + ".db")
        val password = ByteArray(32).also(SecureRandom()::nextBytes)
        val db = SQLiteDatabase.openOrCreateDatabase(path, password, null, null, null)
        try {
            VaultSchema.initializeOrValidate(db)
            db.execSQL("INSERT INTO family_member VALUES('self','Synthetic','Self',1)")
            db.execSQL("INSERT INTO holding VALUES('h1','self',NULL,'Fund','MUTUAL_FUND',0,125000)")
            db.execSQL(
                "INSERT INTO ledger_event(id,owner_id,holding_id,kind,event_date,quantity_delta_scaled,amount_scaled) " +
                    "VALUES ('e1','self','h1','BUY','2026-10-08',100000000,125000)"
            )
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE ledger_event SET amount_scaled=0 WHERE id='e1'")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM ledger_event WHERE id='e1'")
            }
            db.rawQuery("SELECT count(*) FROM ledger_event", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
        } finally {
            db.close()
            password.fill(0)
            SQLiteDatabase.deleteDatabase(path)
        }
    }

    @Test fun authenticatedBackupRestoresAtomicallyOnlyIntoEmptyVault() = runBlocking {
        vault.openOrCreate()
        vault.createMember(FamilyMember(owner, "Synthetic Self", "Self", true))
        vault.createHolding(holding)
        val event = LedgerEvent("e1", holding.id, owner, LedgerKind.BUY,
            "2026-10-08", 200_000_000, MoneyScaled(250_000), "fingerprint-1")
        vault.appendEvent(event)
        val password = "synthetic secure backup password".toCharArray()
        val bytes = vault.exportEncryptedBackup(password)
        assertTrue(bytes.copyOfRange(0, 8).contentEquals("GUDNBACK".toByteArray()))
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("Synthetic Self"))
        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.restoreEncryptedBackup("wrong backup password".toCharArray(), bytes) }
        }
        val tampered = bytes.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        assertThrows(VaultUnavailable::class.java) {
            runBlocking { vault.restoreEncryptedBackup(password, tampered) }
        }
        val secondAlias = "in.growurdhan.test." + UUID.randomUUID()
        val secondRoot = File(context.noBackupFilesDir, "m2-restore-" + UUID.randomUUID())
        val target = SqlCipherVaultRepository(context, secondRoot, secondAlias)
        try {
            target.openOrCreate()
            target.restoreEncryptedBackup(password, bytes)
            assertEquals(vault.members(), target.members())
            assertEquals(vault.holdings(), target.holdings())
            assertEquals(vault.events(), target.events())
            assertThrows(IllegalStateException::class.java) {
                runBlocking { target.restoreEncryptedBackup(password, bytes) }
            }
            assertEquals(1, target.health().eventCount)
        } finally {
            target.close()
            secondRoot.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(secondAlias)
            password.fill('\u0000')
        }
    }
}
