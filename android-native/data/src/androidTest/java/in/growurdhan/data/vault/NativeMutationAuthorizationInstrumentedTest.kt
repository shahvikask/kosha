package `in`.growurdhan.data.vault

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.domain.vault.VaultAuthorization
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses a unique synthetic SQLCipher vault. The authorization is revoked
 * while a real SQL transaction is underway, before it is committed.
 */
@RunWith(AndroidJUnit4::class)
class NativeMutationAuthorizationInstrumentedTest {
    private class SyntheticAuthorization : VaultAuthorization {
        var revision = 1L
        var checks = 0
        var invalid = false
        var failAtCheck = 2
        var replaceAtCheck = Int.MAX_VALUE

        override fun sessionRevision(): Long = revision
        override fun requireUnlocked() = check(!invalid)
        override fun requireRecentApproval() {
            checks++
            if (checks == failAtCheck) invalid = true
            if (checks == replaceAtCheck) revision++
            check(!invalid) { "Synthetic approval revoked" }
        }
    }

    private fun withVault(
        gate: SyntheticAuthorization,
        action: suspend (SqlCipherVaultRepository) -> Unit,
    ): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nonce = UUID.randomUUID().toString()
        val folder = File(context.noBackupFilesDir, "mutation-auth-test-$nonce")
        val alias = "in.growurdhan.mutation.test.$nonce"
        val vault = SqlCipherVaultRepository(
            context, folder, alias, mutationAuthorization = gate,
        )
        try {
            vault.openOrCreate()
            action(vault)
        } finally {
            vault.close()
            folder.deleteRecursively()
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
        }
    }

    @Test fun expiredApprovalRollsBackRealFamilyMemberInsert() {
        val gate = SyntheticAuthorization()
        withVault(gate) { vault ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { vault.registerFamilyMember("Synthetic Primary", "Self") }
            }
            gate.invalid = false
            gate.failAtCheck = Int.MAX_VALUE
            assertTrue(vault.members().isEmpty())
            val member = vault.registerFamilyMember("Synthetic Primary", "Self")
            assertTrue(member.isPrimary)
            assertEquals(1, vault.members().size)
        }
    }

    @Test fun replacedCredentialRollsBackTransactionEvenIfStillUnlocked() {
        val gate = SyntheticAuthorization().also {
            it.failAtCheck = Int.MAX_VALUE
            it.replaceAtCheck = 2
        }
        withVault(gate) { vault ->
            assertThrows(IllegalStateException::class.java) {
                runBlocking { vault.registerFamilyMember("Synthetic Primary", "Self") }
            }
            assertTrue(vault.members().isEmpty())
            gate.replaceAtCheck = Int.MAX_VALUE
            val member = vault.registerFamilyMember("Synthetic Primary", "Self")
            assertTrue(member.isPrimary)
        }
    }
}
