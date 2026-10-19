package `in`.growurdhan.database

import org.junit.Assert.assertThrows
import org.junit.Test

class RestorePreviewBindingTest {
    private val archive = "a".repeat(64)
    private val payload = "b".repeat(64)
    private val approved = RestorePreviewBinding.Review(archive, payload, 4, true)

    @Test fun acceptedOnlyWhenExactOriginalAndAuthenticatedPayloadMatch() {
        RestorePreviewBinding.requireSameAndApproved(approved, archive, payload, 4)
    }

    @Test fun rejectsUnapprovedChangedTamperedOrEmptyRestore() {
        val bad = listOf(
            approved.copy(approved = false),
            approved.copy(archiveSha256 = "c".repeat(64)),
            approved.copy(authenticatedPayloadSha256 = "c".repeat(64)),
            approved.copy(rowCount = 0),
            approved.copy(archiveSha256 = "not-a-hash"),
            approved.copy(rowCount = 5)
        )
        bad.forEach { review ->
            assertThrows(IllegalArgumentException::class.java) {
                RestorePreviewBinding.requireSameAndApproved(review, archive, payload, 4)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestorePreviewBinding.requireSameAndApproved(approved, "c".repeat(64), payload, 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RestorePreviewBinding.requireSameAndApproved(approved, archive, "c".repeat(64), 4)
        }
    }
}
