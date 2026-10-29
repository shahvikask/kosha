package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeGmailSchedulingContractInstrumentedTest {
    @Test fun cadenceAndWorkNamesContainNoAccountIdentifiers() {
        assertEquals("native-gmail-monthly", NativeGmailSyncSchedule.PERIODIC)
        assertEquals("native-gmail-manual", NativeGmailSyncSchedule.MANUAL)
    }
    @Test fun unconfiguredProviderNeverReturnsData() {
        assertThrows(IllegalStateException::class.java) {
            UnconfiguredNativeGmailGateway.fetch(
                NativeGmailGateway.Account("a@example.com", "owner-a"), null)
        }
    }
}
