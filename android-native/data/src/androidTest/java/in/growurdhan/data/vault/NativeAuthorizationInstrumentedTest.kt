package `in`.growurdhan.data.vault

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.core.model.*
import `in`.growurdhan.domain.vault.VaultAuthorization
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests the actual production Hilt-bound DefaultVaultRepository guard without
 * touching any existing vault. All access is denied before SQLCipher is opened.
 */
@RunWith(AndroidJUnit4::class)
class NativeAuthorizationInstrumentedTest {
    private class Locked : VaultAuthorization {
        var checkedRead = 0
        var checkedWrite = 0
        override fun requireUnlocked(): Unit {
            checkedRead++
            throw SecurityException("No verified device session")
        }
        override fun requireRecentApproval(): Unit {
            checkedWrite++
            throw SecurityException("No recently confirmed device credential")
        }
    }

    @Test fun repositoryRejectsOpenAndAllFinancialReadersWhenSessionLocked(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = Locked()
        val repository = DefaultVaultRepository(context, gate)
        try {
            assertThrows(SecurityException::class.java) { runBlocking { repository.openOrCreate() } }
            assertThrows(SecurityException::class.java) { runBlocking { repository.members() } }
            assertThrows(SecurityException::class.java) { runBlocking { repository.holdings() } }
            assertThrows(SecurityException::class.java) { runBlocking { repository.events() } }
            assertThrows(SecurityException::class.java) { runBlocking { repository.readBook() } }
            assertThrows(SecurityException::class.java) { runBlocking { repository.health() } }
            assertEquals(6, gate.checkedRead)
        } finally {
            repository.close()
        }
    }

    @Test fun repositoryRejectsAllFinancialWritesWithoutFreshAuthentication(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = Locked()
        val repository = DefaultVaultRepository(context, gate)
        val owner = OwnerId("user")
        val holding = HoldingId("equity")
        try {
            assertThrows(SecurityException::class.java) {
                runBlocking { repository.createMember(FamilyMember(owner, "Synthetic", "Self", true)) }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { repository.registerFamilyMember("Synthetic", "Self") }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { repository.createAccount(CustodyAccount("acct", owner, "demat", "masked")) }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking {
                    repository.createHolding(Holding(
                        holding, owner, null, "Synthetic", "EQUITY",
                        QuantityScaled(0), MoneyScaled(100_000),
                    ))
                }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking {
                    repository.appendEvent(LedgerEvent(
                        "e1", holding, owner, LedgerKind.BUY,
                        "2026-10-08", 100_000_000, MoneyScaled(100_000), null,
                    ))
                }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { repository.appendFinancialEvents(emptyList()) }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking {
                    repository.importSnapshot(StatementSnapshot(
                        "snap", owner, "a".repeat(64), "2026-10-08",
                        true, listOf(StatementPosition(holding, QuantityScaled(0), MoneyScaled(0))),
                    ))
                }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking { repository.exportEncryptedBackup("test".toCharArray()) }
            }
            assertThrows(SecurityException::class.java) {
                runBlocking {
                    repository.restoreEncryptedBackup("test".toCharArray(), byteArrayOf(0x01))
                }
            }
            assertEquals(9, gate.checkedWrite)
        } finally {
            repository.close()
        }
    }

    @Test fun reviewPreparationRequiresSessionEvenWithWellFormedInput(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gate = Locked()
        val repository = DefaultVaultRepository(context, gate)
        try {
            assertThrows(SecurityException::class.java) {
                runBlocking {
                    repository.prepareStatementReview(
                        "review", OwnerId("owner"), "b".repeat(64),
                        listOf(`in`.growurdhan.domain.finance.StatementLine(
                            HoldingId("holding"), OwnerId("owner"),
                            LocalDate.parse("2026-10-08"), 100_000_000L, MoneyScaled(50_000),
                        )),
                    )
                }
            }
            assertEquals(1, gate.checkedRead)
        } finally {
            repository.close()
        }
    }
}
