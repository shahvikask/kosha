package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeDeviceAdmissionTest {
    @Test fun unsolicitedAndBackgroundResultsDoNotAdmit() {
        val gate = NativeDeviceAdmission()
        assertFalse(gate.complete(true))
        assertFalse(gate.begin())
        gate.onStart()
        assertFalse(gate.complete(true))
        assertFalse(gate.valid())
    }
    @Test fun successfulExplicitForegroundChallengeAdmitsUntilBackground() {
        val gate = NativeDeviceAdmission()
        gate.onStart()
        assertTrue(gate.begin())
        assertTrue(gate.complete(true))
        assertTrue(gate.valid())
        gate.onStop()
        assertFalse(gate.valid())
        gate.onStart()
        assertFalse(gate.valid())
        assertFalse(gate.complete(true))
    }
    @Test fun canceledOrFailedResultNeverAdmits() {
        val gate = NativeDeviceAdmission()
        gate.onStart()
        assertTrue(gate.begin())
        assertFalse(gate.complete(false))
        assertTrue(gate.begin())
        gate.cancel()
        assertFalse(gate.complete(true))
    }
    @Test fun osChallengeCanReturnAfterActivityWasPaused() {
        val gate = NativeDeviceAdmission()
        gate.onStart()
        assertTrue(gate.begin())
        gate.onStop()
        assertFalse(gate.complete(true))
        gate.onStart()
        assertFalse(gate.valid())
        assertTrue(gate.begin())
        gate.onStop()
        gate.onStart()
        assertTrue(gate.complete(true))
        assertTrue(gate.valid())
    }
}