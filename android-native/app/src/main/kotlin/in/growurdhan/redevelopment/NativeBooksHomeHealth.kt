package `in`.growurdhan.redevelopment

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import `in`.growurdhan.model.NativeAssetTaxonomy

/**
 * Read-only P25 Home books-health facts from the admitted SQLCipher snapshot.
 * Counts and dates only: no inferred invested amount, quote, net worth, or tax.
 * Owner isolation is enforced INSIDE this helper, not left to a UI caller.
 */
internal object NativeBooksHomeHealth {
    data class Summary(
        val active: Int,
        val archived: Int,
        val unknownCost: Int,
        val missingDatedPrice: Int,
        val staleDatedPrice: Int,
        val invalidOrFuturePrice: Int,
        val needsReview: Int,
        val categories: List<Pair<String, Int>>
    )

    fun summarize(holdings: List<NativeBooksRepository.Holding>,
        memberId: String, asOf: LocalDate): Summary {
        val scoped = holdings.filter { memberId.isEmpty() || it.personId == memberId }
        val active = scoped.filterNot { it.archived }
        val pricedAssets = active.filter {
            it.category != "LIABILITY" && it.quantityScaled > 0L
        }
        fun ageOrNull(date: String?): Long? {
            if (date == null) return null
            val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
                ?: return null
            return ChronoUnit.DAYS.between(parsed, asOf)
        }
        return Summary(
            active.size, scoped.size - active.size,
            active.count { it.category != "LIABILITY" && it.unknownCost },
            pricedAssets.count { it.priceDate == null },
            pricedAssets.count { it.priceDate != null &&
                (ageOrNull(it.priceDate) ?: -1L) >= 45L },
            pricedAssets.count { it.priceDate != null &&
                (ageOrNull(it.priceDate) ?: -1L) < 0L },
            active.count { it.needsReview },
            NativeAssetTaxonomy.subcategories.keys.mapNotNull { category ->
                val count = active.count { it.category == category }
                if (count == 0) null else category to count
            }
        )
    }
}
