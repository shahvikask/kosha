package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.VaultHealth
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultHealthGateTest {
    @Test fun emptyButValidEncryptedBookIsAllowed() {
        assertTrue(VaultHealthGate.accepts(VaultHealth("ok", 0, 0, 0, 0)))
    }

    @Test fun corruptCipherOrBrokenReferencesFailClosed() {
        assertFalse(VaultHealthGate.accepts(VaultHealth("corrupt", 0, 1, 2, 3)))
        assertFalse(VaultHealthGate.accepts(VaultHealth("unavailable", 0, 1, 2, 3)))
        assertFalse(VaultHealthGate.accepts(VaultHealth("ok", 1, 1, 2, 3)))
    }

    @Test fun impossibleRecordCountsCannotBePresentedAsHealthy() {
        assertFalse(VaultHealthGate.accepts(VaultHealth("ok", 0, -1, 0, 0)))
        assertFalse(VaultHealthGate.accepts(VaultHealth("ok", 0, 0, -1, 0)))
        assertFalse(VaultHealthGate.accepts(VaultHealth("ok", 0, 0, 0, -1)))
        assertFalse(VaultHealthGate.accepts(VaultHealth("ok", -1, 0, 0, 0)))
    }
}
