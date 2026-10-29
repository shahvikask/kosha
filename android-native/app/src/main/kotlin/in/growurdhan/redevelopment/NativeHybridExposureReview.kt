package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeExposureAllocation
import java.math.BigInteger
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * P24: owner-supplied classification metadata, not an issuer-confirmed allocation.
 * No prices, ownership, transaction facts or acquisition costs are written here.
 */
internal object NativeHybridExposureReview {
    data class Record(
        val holdingId: String,
        val ownerId: String,
        val asOf: String,
        val sourceLabel: String,
        val estimated: Boolean,
        val slices: List<NativeExposureAllocation.Slice>
    )
    private val allowed = setOf("EQUITY", "DEBT", "GOLD", "PROPERTY",
        "CASH", "PRIVATE", "PROTECTION", "LIABILITY")
    fun key(id: String): String {
        require(id.matches(Regex("[0-9a-fA-F-]{36}"))) { "Untrusted holding ID" }
        return "native.exposure.review.$id"
    }
    fun split(text: String): List<NativeExposureAllocation.Slice> {
        require(text.length in 5..180) { "Split text required" }
        val rows = text.split(",").map { part ->
            val chunks = part.trim().split(":")
            require(chunks.size == 2 && chunks[0] in allowed &&
                chunks[1].matches(Regex("[1-9][0-9]{0,4}"))) {
                "Each split must use TYPE:positive-bps"
            }
            NativeExposureAllocation.Slice(chunks[0], chunks[1].toInt())
        }
        NativeExposureAllocation.apportion(BigInteger.ZERO, rows)
        return rows
    }
    fun create(holdingId: String, ownerId: String, day: String, source: String,
        estimated: Boolean, splitInput: String): Record {
        key(holdingId)
        require(ownerId.isNotBlank()) { "Missing owner identity" }
        val canonicalDate = LocalDate.parse(day).toString()
        require(day == canonicalDate && !LocalDate.parse(day).isAfter(LocalDate.now())) {
            "Future/noncanonical split date"
        }
        val label = source.trim()
        require(label.length in 8..120 && label.none { it.isISOControl() }) {
            "Owner review source description is required"
        }
        return Record(holdingId, ownerId, day, label, estimated, split(splitInput))
    }
    fun encode(record: Record): String {
        val r = create(record.holdingId, record.ownerId, record.asOf,
            record.sourceLabel, record.estimated,
            record.slices.joinToString(",") { "${it.exposure}:${it.basisPoints}" })
        return JSONObject().put("version", 1).put("holdingId", r.holdingId)
            .put("ownerId", r.ownerId).put("asOf", r.asOf)
            .put("sourceLabel", r.sourceLabel).put("estimated", r.estimated)
            .put("slices", JSONArray().apply {
                for (slice in r.slices) put(JSONObject().put("exposure", slice.exposure)
                    .put("basisPoints", slice.basisPoints))
            }).toString()
    }
    fun decode(text: String): Record {
        require(text.length in 1..2048) { "Invalid encrypted split record length" }
        val obj = JSONObject(text)
        require(obj.getInt("version") == 1) { "Unsupported exposure record" }
        val array = obj.getJSONArray("slices")
        require(array.length() in 1..8) { "Invalid split count" }
        val slices = (0 until array.length()).map {
            val part = array.getJSONObject(it)
            NativeExposureAllocation.Slice(
                part.getString("exposure"), part.getInt("basisPoints"))
        }
        return create(obj.getString("holdingId"), obj.getString("ownerId"),
            obj.getString("asOf"), obj.getString("sourceLabel"),
            obj.getBoolean("estimated"),
            slices.joinToString(",") { "${it.exposure}:${it.basisPoints}" })
    }
    data class Report(
        val summary: NativeExposureAllocation.Summary,
        val awaitingReview: Int,
        val excludedStaleOrUnknown: Int
    )
    fun report(holdings: List<NativeBooksRepository.Holding>, reviews: List<Record>,
        ownerFilter: String, asOf: LocalDate): Report {
        val eligible = holdings.filter { it.category == "MUTUAL_FUND" &&
            it.subcategory == "Hybrid fund" && !it.archived &&
            (ownerFilter.isEmpty() || it.personId == ownerFilter) }
        require(eligible.map { it.id }.distinct().size == eligible.size)
        val indexed = eligible.associateBy { it.id }
        require(reviews.map { it.holdingId }.distinct().size == reviews.size &&
            reviews.all { indexed[it.holdingId]?.personId == it.ownerId }) {
            "Foreign or duplicate reviewed hybrid"
        }
        val positions = reviews.map { review ->
            val h = indexed.getValue(review.holdingId)
            val day = h.priceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val qualified = h.quantityScaled > 0 && h.marketPriceMinor > 0 &&
                !h.needsReview && !h.priceSource.isNullOrBlank() &&
                day != null && !day.isAfter(asOf) &&
                !day.isBefore(asOf.minusDays(45))
            val value = if (qualified) BigInteger.valueOf(h.quantityScaled)
                .multiply(BigInteger.valueOf(h.marketPriceMinor))
                .divide(BigInteger.valueOf(100_000_000L)) else null
            NativeExposureAllocation.Position(h.id, h.personId, value,
                review.slices, review.estimated,
                "Owner-supplied ${review.sourceLabel}")
        }
        val summary = NativeExposureAllocation.summarize(positions, ownerFilter)
        return Report(summary, eligible.size - reviews.size,
            summary.excludedUnpriced)
    }
}
