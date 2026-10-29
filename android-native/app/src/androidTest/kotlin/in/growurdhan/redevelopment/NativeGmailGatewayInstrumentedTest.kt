package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeGmailGatewayInstrumentedTest {
    private val accountA = NativeGmailGateway.Account("one@example.com", "owner-a")
    private val accountB = NativeGmailGateway.Account("one@example.com", "owner-b")
    private val proof = NativeGmailGateway.Attachment("message1", "attach1", "a".repeat(64))

    @Test fun missingOAuthFailsClosed() {
        assertThrows(IllegalStateException::class.java) {
            UnconfiguredNativeGmailGateway.fetch(accountA, null)
        }
    }

    @Test fun exactAccountAndOwnerMappingIsRequired() {
        val p = NativeGmailGateway.Page(accountA, "100", true, listOf(proof))
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(p)))
        assertEquals(p, fake.fetch(accountA, null))
        assertThrows(IllegalStateException::class.java) {
            fake.fetch(accountB, null)
        }
    }

    @Test fun unknownAttachmentIsQuarantinedAndBlocksCursor() {
        val p = NativeGmailGateway.Page(accountA, "101", true, listOf(proof))
        val result = NativeGmailPageGate.assess(p, emptySet())
        assertNull(result.candidateCursor)
        assertEquals(listOf(proof), result.quarantine)
        assertEquals("101", NativeGmailPageGate.assess(
            p, setOf(proof.sourceSha256)).candidateCursor)
    }

    @Test fun incompletePaginationCannotPublishCursor() {
        val p = NativeGmailGateway.Page(accountA, "102", false,
            listOf(proof), nextPageToken = "1")
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(p)))
        assertNull(NativeGmailPageGate.assess(fake.fetch(accountA, null),
            setOf(proof.sourceSha256)).candidateCursor)
    }

    @Test fun malformedPagesFailClosed() {
        val invalid = NativeGmailGateway.Page(accountA, "invalid", true, emptyList())
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(invalid)))
        assertThrows(IllegalArgumentException::class.java) {
            fake.fetch(accountA, null)
        }
    }

    @Test fun paginationIsBoundedAndRemainsReadOnly() {
        val first = NativeGmailGateway.Page(accountA, "110", false, listOf(proof),
            nextPageToken = "1")
        val last = NativeGmailGateway.Page(accountA, "111", true, listOf(proof))
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(first, last)))
        val scan = NativeGmailScanPlanner.scan(fake, accountA, null)
        assertEquals("111", scan.finalHistoryId)
        assertEquals(listOf(proof), scan.attachments)
    }

    @Test fun conflictingAttachmentBytesFailBeforeReview() {
        val conflicting = proof.copy(sourceSha256 = "b".repeat(64))
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(
            NativeGmailGateway.Page(accountA, "110", false, listOf(proof), "1"),
            NativeGmailGateway.Page(accountA, "111", true, listOf(conflicting)))))
        assertThrows(IllegalArgumentException::class.java) {
            NativeGmailScanPlanner.scan(fake, accountA, null)
        }
    }

    @Test fun incompleteHistoryCannotBeCommittedAsCompleteScan() {
        val p = NativeGmailGateway.Page(accountA, "112", false, emptyList(), "1")
        val fake = FakeNativeGmailGateway(mapOf(accountA to listOf(p, p)))
        assertThrows(IllegalArgumentException::class.java) {
            NativeGmailScanPlanner.scan(fake, accountA, null)
        }
    }
}
