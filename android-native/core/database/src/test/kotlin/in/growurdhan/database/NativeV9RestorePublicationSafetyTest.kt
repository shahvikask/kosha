package `in`.growurdhan.database

import java.nio.file.Files
import java.nio.file.StandardOpenOption
import org.junit.Assert.*
import org.junit.Test

/** Synthetic filesystem crash/low-storage publication scenarios. */
class NativeV9RestorePublicationSafetyTest {
    @Test fun durableSnapshotClaimSurvivesCallerFailureAndCannotBeReplayed() {
        val dir = Files.createTempDirectory("native-v9-claim-")
        val fingerprint = "a".repeat(64)
        val journal = RestoreOnceJournal(dir)
        journal.claimOnce(fingerprint)
        assertTrue(journal.hasClaim(fingerprint))
        assertThrows(IllegalStateException::class.java) {
            RestoreOnceJournal(dir).claimOnce(fingerprint)
        }
        val claim = dir.resolve("restore-" + fingerprint + ".claim")
        assertEquals("GUD_RESTORE_CLAIM_V1\n" + fingerprint + "\n",
            String(Files.readAllBytes(claim), Charsets.US_ASCII))
        assertTrue(journal.hasClaim(fingerprint))
    }

    @Test fun missingOrTruncatedCandidateFailsBeforeLiveReservation() {
        val dir = Files.createTempDirectory("native-v9-no-space-")
        val candidate = dir.resolve("vault.sqlcipher.restore")
        val live = dir.resolve("vault.sqlcipher")
        assertThrows(Exception::class.java) {
            EncryptedRestorePublisher.publishReservedAtomic(candidate, live)
        }
        assertFalse(Files.exists(live))
        Files.write(candidate, byteArrayOf(), StandardOpenOption.CREATE_NEW)
        assertThrows(Exception::class.java) {
            EncryptedRestorePublisher.publishReservedAtomic(candidate, live)
        }
        assertEquals(0L, Files.size(candidate))
        assertFalse(Files.exists(live))
    }

    @Test fun previouslyPublishedOrInterruptedReservationCannotBeOverwritten() {
        val dir = Files.createTempDirectory("native-v9-no-clobber-")
        val candidate = dir.resolve("restore.sqlcipher")
        val live = dir.resolve("vault.sqlcipher")
        val original = byteArrayOf(11, 22, 33, 44)
        val archived = byteArrayOf(9, 8, 7, 6)
        Files.write(candidate, archived, StandardOpenOption.CREATE_NEW)
        Files.write(live, original, StandardOpenOption.CREATE_NEW)
        assertThrows(Exception::class.java) {
            EncryptedRestorePublisher.publishReservedAtomic(candidate, live)
        }
        assertArrayEquals(original, Files.readAllBytes(live))
        assertArrayEquals(archived, Files.readAllBytes(candidate))
        Files.delete(live)
        Files.createFile(live) // crash after CREATE_NEW reservation
        assertThrows(Exception::class.java) {
            EncryptedRestorePublisher.publishReservedAtomic(candidate, live)
        }
        assertEquals(0L, Files.size(live))
        assertArrayEquals(archived, Files.readAllBytes(candidate))
    }
}
