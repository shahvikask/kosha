package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic isolated encrypted-vault integration, never a user's vault. */
@RunWith(AndroidJUnit4::class)
class NativeFamilyOnboardingInstrumentedTest {
    private lateinit var folder: File
    private lateinit var alias: String
    private lateinit var vault: SqlCipherVaultRepository

    @Before fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = UUID.randomUUID().toString()
        folder = File(context.noBackupFilesDir, "family-onboarding-test-$suffix")
        alias = "in.growurdhan.family.test.$suffix"
        vault = SqlCipherVaultRepository(context, folder, alias)
    }

    @After fun tearDown(): Unit = runBlocking {
        vault.close()
        folder.deleteRecursively()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }

    @Test fun firstAndDependentMembersPersistAcrossEncryptedReopen(): Unit = runBlocking {
        vault.openOrCreate()
        val first = vault.registerFamilyMember("  Synthetic   Primary  ", "Self")
        assertEquals("Synthetic Primary", first.displayName)
        assertTrue(first.isPrimary)
        assertEquals("Self", first.relationship)
        assertEquals(36, first.id.raw.length)
        assertFalse(first.id.raw.contains(first.displayName))

        val spouse = vault.registerFamilyMember("Synthetic Spouse", "Spouse")
        assertFalse(spouse.isPrimary)
        assertEquals("Spouse", spouse.relationship)
        assertFalse(first.id == spouse.id)
        assertEquals(2, vault.health().memberCount)
        vault.close()
        vault.openOrCreate()
        val persisted = vault.members()
        assertEquals(setOf(first, spouse), persisted.toSet())
        assertEquals(1, persisted.count { it.isPrimary })
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun invalidAndDuplicateRegistrationsRollbackEntireTransaction(): Unit = runBlocking {
        vault.openOrCreate()
        val first = vault.registerFamilyMember("Synthetic Primary", "Self")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("Synthetic Another", "Self") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("synthetic primary", "Child") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("  ", "Child") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("Dependent", "Unknown") }
        }
        assertEquals(listOf(first), vault.members())
        val dependent = vault.registerFamilyMember("Synthetic Child", "Child")
        assertEquals(setOf(first, dependent), vault.members().toSet())
        assertEquals(0, vault.health().foreignKeyViolations)
    }

    @Test fun wrongFirstRelationshipDoesNotCreatePrimaryOrEmptyVaultData(): Unit = runBlocking {
        vault.openOrCreate()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("Synthetic Spouse", "Spouse") }
        }
        assertTrue(vault.members().isEmpty())
        val primary = vault.registerFamilyMember("Synthetic", "Self")
        assertTrue(primary.isPrimary)
    }

    @Test fun inconsistentLegacyBookWithoutPrimaryFailsClosedWithoutRewrite(): Unit = runBlocking {
        vault.openOrCreate()
        val old = FamilyMember(OwnerId("legacy"), "Existing Legacy", "Parent", false)
        vault.createMember(old) // The pre-existing lower-level API remains unchanged.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { vault.registerFamilyMember("Synthetic", "Child") }
        }
        assertEquals(listOf(old), vault.members())
        assertEquals(0, vault.health().foreignKeyViolations)
    }
}
