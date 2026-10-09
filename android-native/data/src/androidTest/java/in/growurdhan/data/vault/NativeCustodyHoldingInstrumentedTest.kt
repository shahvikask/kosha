package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.*
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Fully synthetic encrypted-vault tests. No user accounts or financial documents. */
@RunWith(AndroidJUnit4::class)
class NativeCustodyHoldingInstrumentedTest {
    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var alias: String
    private lateinit var vault: SqlCipherVaultRepository
    private val owner = OwnerId("self")
    private val spouse = OwnerId("spouse")

    @Before fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        folder = File(context.noBackupFilesDir, "cas-onboarding-$suffix")
        alias = "in.growurdhan.test.m7.$suffix"
        vault = SqlCipherVaultRepository(context, folder, alias)
    }

    @After fun cleanup(): Unit = runBlocking {
        vault.close()
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    private suspend fun seed(): CustodyAccount {
        vault.openOrCreate()
        vault.registerFamilyMember("Synthetic Primary", "Self")
        val actualOwner = vault.members().single().id
        return vault.registerCustodyAccount(actualOwner, "CAMS", "Synthetic registrar")
    }

    @Test fun accountAndEnrolledHoldingPersistAcrossEncryptedReopen(): Unit = runBlocking {
        val account = seed()
        val holding = vault.registerCasHolding(
            account.ownerId, account.id, "Synthetic Index", "INF000000001", "FOL123",
        )
        assertEquals(0L, holding.quantity.raw)
        assertEquals(0L, holding.currentUnitPrice.raw)
        assertEquals("MUTUAL_FUND", holding.assetCategory)
        assertTrue(vault.events().isEmpty())
        assertEquals(account, vault.accounts().single())
        assertEquals(holding.id, vault.casIdentities().single().holdingId)
        vault.close()
        assertEquals(3, vault.openOrCreate().schemaVersion)
        assertEquals(account, vault.accounts().single())
        assertEquals(holding, vault.holdings().single())
        assertEquals("INF000000001", vault.casIdentities().single().isin)
        assertEquals("FOL123", vault.casIdentities().single().folio)
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun wrongOwnerAndMissingAccountCannotEnrolOrWritePartialHolding(): Unit = runBlocking {
        val account = seed()
        vault.createMember(FamilyMember(spouse, "Synthetic Spouse", "Spouse", false))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.registerCasHolding(spouse, account.id, "Synthetic", "INF000000001", "123")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.registerCasHolding(owner, "not-a-registrar", "Synthetic", "INF000000001", "123")
            }
        }
        assertTrue(vault.holdings().isEmpty())
        assertTrue(vault.casIdentities().isEmpty())
    }

    @Test fun duplicateIdentityDoesNotLeaveSecondHoldingOrFakePurchase(): Unit = runBlocking {
        val account = seed()
        vault.registerCasHolding(account.ownerId, account.id, "Synthetic", "INF000000001", "123")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.registerCasHolding(account.ownerId, account.id, "Duplicate", "INF000000001", "123")
            }
        }
        assertEquals(1, vault.holdings().size)
        assertEquals(1, vault.casIdentities().size)
        assertTrue(vault.events().isEmpty())
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun invalidIsinFolioAndRegistrarAccountCannotMutateVault(): Unit = runBlocking {
        val account = seed()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.registerCasHolding(account.ownerId, account.id, "Synthetic", "NOT-ISIN", "123")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                vault.registerCasHolding(account.ownerId, account.id, "Synthetic", "INF000000001", "a b")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerCustodyAccount(account.ownerId, "OTHER", "Bad") }
        }
        assertEquals(1, vault.accounts().size)
        assertTrue(vault.holdings().isEmpty())
        assertTrue(vault.casIdentities().isEmpty())
    }

    @Test fun portableV3BackupRestoresExactEnrolledCasIdentity(): Unit = runBlocking {
        val account = seed()
        vault.registerCasHolding(account.ownerId, account.id, "Synthetic", "INF000000001", "123")
        val password = "synthetic portable backup password".toCharArray()
        val bytes = vault.exportEncryptedBackup(password)
        val restoreRoot = File(context.noBackupFilesDir, "m7-restore-" + UUID.randomUUID())
        val restoreAlias = "in.growurdhan.restore.m7." + UUID.randomUUID()
        val target = SqlCipherVaultRepository(context, restoreRoot, restoreAlias)
        try {
            target.openOrCreate()
            target.restoreEncryptedBackup(password, bytes)
            assertEquals(vault.readBook(), target.readBook())
            assertEquals(1, target.casIdentities().size)
            assertEquals(0, target.health().foreignKeyViolations)
        } finally {
            password.fill('\u0000')
            target.close()
            restoreRoot.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(restoreAlias)
        }
    }

    @Test fun legacyV2LogicalBackupRestoresWithoutInventingCasIdentity(): Unit = runBlocking {
        val account = seed()
        val pass = "synthetic legacy format password".toCharArray()
        val bytes = vault.exportEncryptedBackup(pass)
        val payload = JSONObject(VaultBackupCodec.decrypt(bytes, pass).toString(Charsets.UTF_8))
        payload.put("format", 2)
        payload.put("databaseVersion", 2)
        payload.remove("holding_cas_identity")
        val oldBytes = VaultBackupCodec.encrypt(payload.toString().toByteArray(Charsets.UTF_8), pass)
        val root = File(context.noBackupFilesDir, "m7-v2-restore-" + UUID.randomUUID())
        val aliasRestore = "in.growurdhan.v2.restore." + UUID.randomUUID()
        val target = SqlCipherVaultRepository(context, root, aliasRestore)
        try {
            target.openOrCreate()
            target.restoreEncryptedBackup(pass, oldBytes)
            assertEquals(listOf(account), target.accounts())
            assertTrue(target.casIdentities().isEmpty())
            assertEquals(0, target.health().foreignKeyViolations)
        } finally {
            pass.fill('\u0000')
            target.close()
            root.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(aliasRestore)
        }
    }

    @Test fun realSqlCipherV2UpgradesToV3WithoutModifyingExistingFinancialRows(): Unit = runBlocking {
        assertTrue(folder.mkdirs())
        val key = VaultKeyEnvelope(File(folder, "wrapped.key"), alias)
            .unwrapOrCreate(allowCreate = true)
        System.loadLibrary("sqlcipher")
        val db = SQLiteDatabase.openOrCreateDatabase(
            File(folder, "wealth.db"), key, null, null, null,
        )
        try {
            db.execSQL("PRAGMA foreign_keys=ON")
            for (sql in VaultSchema.legacyV1Statements) db.execSQL(sql)
            for (sql in VaultSchema.migrationToV2) db.execSQL(sql)
            db.execSQL("PRAGMA user_version=2")
            db.execSQL(
                "INSERT INTO family_member(id,display_name,relationship,is_primary) " +
                    "VALUES('self','Old synthetic owner','Self',1)"
            )
            db.execSQL(
                "INSERT INTO holding(id,owner_id,display_name,asset_category,quantity_scaled,price_scaled) " +
                    "VALUES('legacy','self','Old synthetic fund','MUTUAL_FUND',0,0)"
            )
            db.execSQL(
                "INSERT INTO ledger_event(id,owner_id,holding_id,kind,event_date,quantity_delta_scaled,amount_scaled) " +
                    "VALUES('evt','self','legacy','BUY','2026-01-01',100000000,10000)"
            )
        } finally {
            db.close()
            key.fill(0)
        }
        File(folder, "ready").writeText("GUDREADY02")
        assertEquals(3, vault.openOrCreate().schemaVersion)
        assertEquals("evt", vault.events().single().id)
        assertTrue(vault.casIdentities().isEmpty())
        assertEquals("Old synthetic fund", vault.holdings().single().name)
        assertEquals(0, vault.health().foreignKeyViolations)
    }
}
