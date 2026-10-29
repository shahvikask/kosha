package `in`.growurdhan.model

/**
 * E16 Play Billing catalog metadata only. A retrieved product listing is NOT
 * payment, entitlement, purchase verification, owner proof, or a trial.
 */
object NativePlaySkuCatalog {
    enum class Type { INAPP, SUBS }
    data class Item(val id: String, val type: Type)
    val expected = listOf(
        Item(NativeCommercialEntitlements.MANUAL, Type.INAPP),
        Item(NativeCommercialEntitlements.GMAIL, Type.INAPP),
        Item(NativeCommercialEntitlements.UPGRADE, Type.INAPP),
        Item(NativeCommercialEntitlements.SUPPORT, Type.SUBS)
    )
    data class Report(val recognized: Int, val missing: Set<String>,
        val invalidOrDuplicate: Boolean) {
        val complete: Boolean get() = recognized == expected.size &&
            missing.isEmpty() && !invalidOrDuplicate
    }
    fun inspect(listed: List<Item>): Report {
        val expectedIds = expected.map { it.id }.toSet()
        val counts = listed.groupingBy { it.id }.eachCount()
        val duplicates = counts.values.any { it != 1 }
        val valid = expected.filter { item -> listed.count { it == item } == 1 &&
            counts[item.id] == 1 }.map { it.id }.toSet()
        return Report(valid.size, expectedIds - valid,
            duplicates || listed.any { item -> expected.none { it == item } })
    }
}
