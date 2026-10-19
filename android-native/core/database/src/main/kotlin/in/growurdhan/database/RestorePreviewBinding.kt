package `in`.growurdhan.database

/** Pure fail-closed binding between a reviewed encrypted restore and publication.
 * Never use a UI checkbox or row counts alone to authorize restoring a different source.
 */
object RestorePreviewBinding {
    data class Review(val archiveSha256: String, val authenticatedPayloadSha256: String,
                      val rowCount: Int, val approved: Boolean)
    fun requireSameAndApproved(review: Review, stagedArchiveSha256: String,
                               authenticatedPayloadSha256: String, rows: Int) {
        require(review.approved && review.rowCount == rows &&
            review.rowCount > 0 &&
            review.archiveSha256.matches(Regex("[a-f0-9]{64}")) &&
            review.authenticatedPayloadSha256.matches(Regex("[a-f0-9]{64}")) &&
            review.archiveSha256 == stagedArchiveSha256 &&
            review.authenticatedPayloadSha256 == authenticatedPayloadSha256) {
            "Restoration preview or explicit approval is invalid"
        }
    }
}
