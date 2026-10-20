package `in`.growurdhan.redevelopment

import `in`.growurdhan.database.RestoreV9Plan
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Strict adapter from the EXACT GCM-authenticated plaintext string to a v9 row plan.
 *
 * Every typed field is read from one immutable source string; a caller cannot
 * substitute unrelated typed records while claiming an authentic source digest.
 * This class never persists, promotes a vault, or approves a device.
 */
internal object FrozenJsonV9Adapter {
    private const val MAX_JSON_BYTES = 64 * 1024 * 1024
    private val primaryRelations = setOf("Self")
    private fun bad(): Nothing = throw IllegalArgumentException("Incompatible restore snapshot")

    private fun string(o: JSONObject, k: String): String {
        val v = if (o.has(k)) o.get(k) else bad()
        if (v !is String || v.isBlank() || v.length > 4096) bad()
        return v
    }

    // Present-but-empty fields are legal in original Holding for categories
    // without a tradable symbol or populated subcategory.
    private fun presentString(o: JSONObject, k: String): String {
        val v = if (o.has(k)) o.get(k) else bad()
        if (v !is String || v.length > 4096) bad()
        return v
    }

    private fun optional(o: JSONObject, k: String): String? {
        if (!o.has(k) || o.isNull(k)) return null
        val v = o.get(k)
        if (v !is String || v.length > 65_536) bad()
        return v
    }

    private fun decimal(o: JSONObject, k: String): String {
        val v = if (o.has(k)) o.get(k) else bad()
        if (v !is String || !v.matches(Regex("-?(0|[1-9][0-9]*)")) ||
            v.length > 20 || v.toLongOrNull() == null) bad()
        return v
    }

    private fun optionalMoney(o: JSONObject, field: String): String? =
        if (!o.has(field) || o.isNull(field)) null else decimal(o, field)

    private fun optionalDate(o: JSONObject, field: String): String? =
        optional(o, field)?.also {
            require(LocalDate.parse(it).toString() == it) {
                "Non-canonical restored date"
            }
        }

    private fun jsonObject(o: JSONObject, field: String): String? {
        if (!o.has(field) || o.isNull(field)) return null
        val value = o.get(field) as? JSONObject ?: bad()
        val raw = value.toString()
        require(raw.length <= 32768) { "Restored metadata too large" }
        return raw
    }

    private fun jsonArray(o: JSONObject, field: String): String? {
        if (!o.has(field) || o.isNull(field)) return null
        val value = o.get(field) as? JSONArray ?: bad()
        val raw = value.toString()
        require(raw.length <= 32768) { "Restored metadata too large" }
        return raw
    }

    private fun percentBps(o: JSONObject, field: String): Long? {
        if (!o.has(field) || o.isNull(field)) return null
        val value = o.get(field)
        require(value is Number) { "Percentage must be a JSON number" }
        val exact = runCatching {
            BigDecimal(value.toString()).movePointRight(2)
                .toBigIntegerExact().longValueExact()
        }.getOrElse { bad() }
        require(exact in 0..100_000) {
            "Cannot losslessly represent original interest rate in basis points"
        }
        return exact
    }

    private fun bool(o: JSONObject, k: String): Boolean? {
        if (!o.has(k) || o.isNull(k)) return null
        return o.get(k) as? Boolean ?: bad()
    }

    private fun int(o: JSONObject, k: String): Int {
        val n = if (o.has(k)) o.get(k) else bad()
        return when (n) {
            is Int -> n
            is Long -> n.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt() ?: bad()
            else -> bad()
        }
    }

    /**
     * The original authenticated JSON is retained verbatim, but retaining a value
     * opaquely must not be mistaken for migrating unknown financial semantics.
     * Reject unrecognized money/quantity fields instead of silently omitting them
     * from the canonical ledger, position or goal.
     */
    private fun rejectUnknownFinancialFields(o: JSONObject, allowed: Set<String>) {
        val financial = Regex("(?i)(amount|minor|scaled|charges?|fees?|cost|price|quantity|units?|value|tax|interest|rate|principal|balance|proceeds|premium|target)")
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowed && financial.containsMatchIn(key)) bad()
        }
    }

    private val holdingFields = setOf("id", "ownerId", "accountId", "name",
        "category", "subCategory", "symbolOrFolio", "quantityScaled",
        "currentPriceMinor", "lastPriceUpdated", "priceSource", "isin",
        "notes", "needsReview", "averageCostPerUnitMinor",
        // Frozen src/types.ts Holding fields: accepted as authenticated opaque
        // original metadata, never silently converted into ledger events.
        "dayChangePercent", "dayChangeMinor", "reviewReasons",
        "benchmarkPriceMinor", "issuePriceMinor", "couponRatePercent",
        "nextCouponDate", "maturityDate", "interestRatePercent",
        "institutionName", "accountNumberMasked", "monthlyEmiMinor",
        "emiStatus", "remainingEmiCount", "acquiredOn", "details",
        "isPriceStale", "fmv31Jan2018Minor", "exposureNode", "l2Node",
        "wrapper", "allocationSplit", "location", "tag", "tags",
        "amc", "rta", "amfiCode", "advisor", "schemeType", "ucc",
        "exchange", "dpName")
    private val eventFields = setOf("id", "holdingId", "holdingName", "ownerId",
        "eventType", "eventDate", "quantityScaled", "unitPriceMinor",
        "totalAmountMinor", "chargesMinor", "createdAt", "supersedesEventId",
        "importBatchId", "sourceRef", "notes")
    private val goalFields = setOf("id", "ownerId", "name", "targetMinor",
        "targetDate", "createdAt", "priority")

    private fun array(parent: JSONObject, k: String, legacy: Boolean): JSONArray {
        if (legacy && !parent.has(k)) return JSONArray()
        if (!parent.has(k) || parent.isNull(k)) bad()
        return parent.get(k) as? JSONArray ?: bad()
    }

    private fun <T> rows(parent: JSONObject, key: String, legacy: Boolean = false, mapper: (JSONObject) -> T): List<T> {
        val a = array(parent, key, legacy)
        if (a.length() > 250_000) bad()
        return List(a.length()) { i -> mapper(a.get(i) as? JSONObject ?: bad()) }
    }

    fun planFromAuthenticatedPlaintext(exactPlaintext: String): RestoreV9Plan.Plan {
        val encodedBytes = exactPlaintext.toByteArray(Charsets.UTF_8)
        require(encodedBytes.size in 1..MAX_JSON_BYTES) { "Invalid authenticated snapshot size" }
        // org.json is permissive: duplicated ledgerEvents/holdings keys or
        // malformed JSON syntax must never be silently collapsed on restore.
        // Check the exact GCM-authenticated text without rewriting a byte.
        AuthenticatedJsonStructure.requireUnambiguous(exactPlaintext)
        val json = JSONObject(exactPlaintext)
        val version = if (json.has("version")) int(json, "version") else 1
        require(version in 1..2 &&
            (version == 1 || string(json, "kind") == "GUDVAULT") &&
            (!json.has("kind") || string(json, "kind") == "GUDVAULT")) {
            "Unknown frozen backup kind or version"
        }
        // Frozen KOSHA1 defaults absent collections to empty, but source
        // timestamp, identity, money and ownership remain mandatory.
        // Financial collection presence is authenticated evidence: missing
        // holdings or ledgerEvents is NOT equivalent to an explicit empty array.
        val login = json.get("account") as? JSONObject ?: bad()
        val record = RestoreV9Plan.Login(
            email = string(login, "email"), firstName = string(login, "firstName"),
            lastName = string(login, "lastName"),
            passwordSalt = string(login, "passwordSalt"),
            passwordHash = string(login, "passwordHash"),
            passwordIterations = int(login, "passwordIterations"),
            passwordChangedAt = string(login, "passwordChangedAt"),
            q1Id = string(login, "q1Id"), q1Hash = string(login, "q1Hash"),
            q2Id = string(login, "q2Id"), q2Hash = string(login, "q2Hash"),
            createdAt = string(login, "createdAt"),
            panMasked = optional(login, "panMasked"),
            panForStatements = optional(login, "panForStatements"),
            dateOfBirth = optional(login, "dateOfBirth")
        )
        val members = rows(json, "familyMembers") { member ->
            val relation = string(member, "relation")
            val primary = (bool(member, "isPrimary") == true) || (relation in primaryRelations)
            RestoreV9Plan.Family(id = string(member, "id"), name = string(member, "name"),
                relation = relation, isPrimary = primary, color = optional(member, "color"),
                panMasked = optional(member, "panMasked"),
                panForStatements = optional(member, "panForStatements"),
                dateOfBirth = optional(member, "dateOfBirth"),
                notes = optional(member, "notes"))
        }
        val accounts = rows(json, "accounts", version == 1) { a ->
            RestoreV9Plan.Account(id = string(a, "id"), ownerId = string(a, "ownerId"),
                accountType = string(a, "accountType"), custodian = string(a, "custodian"),
                identifierMasked = optional(a, "identifierMasked"),
                dpId = optional(a, "dpId"), clientId = optional(a, "clientId"),
                boId = optional(a, "boId"), nominee = optional(a, "nominee"))
        }
        val holdings = rows(json, "holdings") { h ->
            rejectUnknownFinancialFields(h, holdingFields)
            RestoreV9Plan.Holding(id = string(h, "id"), ownerId = string(h, "ownerId"),
                accountId = optional(h, "accountId"), name = string(h, "name"),
                category = string(h, "category"), subCategory = presentString(h, "subCategory"),
                symbolOrFolio = presentString(h, "symbolOrFolio"),
                quantityScaled = decimal(h, "quantityScaled"),
                currentPriceMinor = decimal(h, "currentPriceMinor"),
                lastPriceUpdated = string(h, "lastPriceUpdated"),
                priceSource = string(h, "priceSource"),
                isin = optional(h, "isin"), notes = optional(h, "notes"),
                needsReview = bool(h, "needsReview"),
                averageCostPerUnitMinor = decimal(h, "averageCostPerUnitMinor"),
                metadata = RestoreV9Plan.HoldingMetadata(
                    benchmarkPriceMinor = optionalMoney(h, "benchmarkPriceMinor"),
                    issuePriceMinor = optionalMoney(h, "issuePriceMinor"),
                    couponRateBps = percentBps(h, "couponRatePercent"),
                    interestRateBps = percentBps(h, "interestRatePercent"),
                    nextCouponDate = optionalDate(h, "nextCouponDate"),
                    maturityDate = optionalDate(h, "maturityDate"),
                    institutionName = optional(h, "institutionName"),
                    accountNumberMasked = optional(h, "accountNumberMasked"),
                    monthlyEmiMinor = optionalMoney(h, "monthlyEmiMinor"),
                    emiStatus = optional(h, "emiStatus"),
                    remainingEmiCount = if (h.has("remainingEmiCount") &&
                        !h.isNull("remainingEmiCount")) int(h, "remainingEmiCount").toLong()
                        else null,
                    acquiredOn = optionalDate(h, "acquiredOn"),
                    detailsJson = jsonObject(h, "details"),
                    reviewReasonsJson = jsonArray(h, "reviewReasons"),
                    tagsJson = jsonArray(h, "tags"),
                    location = optional(h, "location"), tag = optional(h, "tag"),
                    fmv31Jan2018Minor = optionalMoney(h, "fmv31Jan2018Minor"),
                    exposureNode = optional(h, "exposureNode"),
                    l2Node = optional(h, "l2Node"), wrapper = optional(h, "wrapper"),
                    allocationSplitJson = jsonArray(h, "allocationSplit"),
                    amc = optional(h, "amc"), rta = optional(h, "rta"),
                    amfiCode = optional(h, "amfiCode"), advisor = optional(h, "advisor"),
                    schemeType = optional(h, "schemeType"), ucc = optional(h, "ucc"),
                    exchange = optional(h, "exchange"), dpName = optional(h, "dpName")))
        }
        val events = rows(json, "ledgerEvents") { e ->
            rejectUnknownFinancialFields(e, eventFields)
            RestoreV9Plan.Event(id = string(e, "id"), holdingId = string(e, "holdingId"),
                holdingName = string(e, "holdingName"), ownerId = string(e, "ownerId"),
                eventType = string(e, "eventType"), eventDate = string(e, "eventDate"),
                quantityScaled = decimal(e, "quantityScaled"),
                unitPriceMinor = decimal(e, "unitPriceMinor"),
                totalAmountMinor = decimal(e, "totalAmountMinor"),
                // Never manufacture a zero charge for a historical ledger event.
                // Absence cannot distinguish waived from unknown transaction costs.
                chargesMinor = decimal(e, "chargesMinor"),
                createdAt = string(e, "createdAt"),
                supersedesEventId = optional(e, "supersedesEventId"),
                importBatchId = optional(e, "importBatchId"),
                sourceRef = optional(e, "sourceRef"), notes = optional(e, "notes"))
        }
        val goals = rows(json, "goals", version == 1) { g ->
            rejectUnknownFinancialFields(g, goalFields)
            RestoreV9Plan.Goal(id = string(g, "id"), ownerId = optional(g, "ownerId"),
                name = string(g, "name"), targetMinor = decimal(g, "targetMinor"),
                targetDate = optional(g, "targetDate"), createdAt = string(g, "createdAt"),
                priority = optional(g, "priority"))
        }
        return RestoreV9Plan.project(
            RestoreV9Plan.Snapshot(exportedAt = string(json, "exportedAt"),
                login = record, families = members, accounts = accounts,
                holdings = holdings, events = events, goals = goals,
                authenticatedOriginalJson = exactPlaintext))
    }
}
