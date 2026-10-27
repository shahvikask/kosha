package `in`.growurdhan.redevelopment

import java.math.BigDecimal
import java.time.LocalDate
import org.json.JSONObject

/**
 * Typed legacy HoldingDetails projection. Keys and JSON value types follow frozen src/types.ts.
 * Only fields visible for the selected category are editable; unknown/historical keys survive.
 * Amounts are exact scaled-integer DECIMAL STRINGS, never floating-point money.
 */
internal object NativeHoldingSpecialist {
    enum class Kind { TEXT, ENUM, DATE, MONEY, DECIMAL, INTEGER, BOOLEAN }
    data class Field(val key: String, val label: String, val kind: Kind,
        val options: List<String> = emptyList())

    private fun text(k: String, title: String) = Field(k, title, Kind.TEXT)
    private fun date(k: String, title: String) = Field(k, title, Kind.DATE)
    private fun money(k: String, title: String) = Field(k, title, Kind.MONEY)
    private fun number(k: String, title: String) = Field(k, title, Kind.DECIMAL)
    private fun integer(k: String, title: String) = Field(k, title, Kind.INTEGER)
    private fun bool(k: String, title: String) = Field(k, title, Kind.BOOLEAN)
    private fun choice(k: String, title: String, vararg values: String) =
        Field(k, title, Kind.ENUM, values.toList())

    fun fields(category: String, subcategory: String): List<Field> = when (category) {
        "EQUITY" -> listOf(choice("depository", "Depository", "CDSL", "NSDL"),
            text("dpId", "DP identifier"), text("clientId", "Client identifier"),
            text("boId", "Beneficial owner identifier"))
        "MUTUAL_FUND" -> listOf(
            choice("mfPlan", "MF plan", "DIRECT", "REGULAR"),
            choice("mfOption", "MF option", "GROWTH", "IDCW"),
            choice("mfHeldAs", "MF custody type", "FOLIO", "DEMAT"),
            choice("depository", "Depository", "CDSL", "NSDL"),
            text("dpId", "DP identifier"), text("clientId", "Client identifier"),
            text("boId", "Beneficial owner identifier"))
        "GOLD_SGB" -> listOf(
            choice("goldHoldMode", "Gold holding mode", "DEMAT", "CERTIFICATE"),
            choice("goldPurity", "Gold purity", "24K", "22K", "999"),
            text("goldHallmark", "Hallmark"), date("earlyExitDate", "Eligible early exit date"))
        "FIXED_INCOME" -> buildList {
            add(money("annualContributionMinor", "Annual contribution ₹"))
            add(date("contributionOn", "Contribution date"))
            add(choice("compounding", "Interest compounding", "MONTHLY", "QUARTERLY",
                "HALF_YEARLY", "YEARLY", "CUMULATIVE"))
            add(bool("tdsApplicable", "TDS applicable"))
            if (subcategory == "NPS") {
                add(choice("npsTier", "NPS tier", "I", "II"))
                add(choice("npsCra", "NPS CRA", "PROT", "CAMS", "KFIN"))
                add(text("npsPfm", "NPS fund manager"))
            }
            add(money("faceValueMinor", "Face value ₹"))
            add(money("monthlyIncomeMinor", "Monthly distribution ₹"))
            add(integer("incomeDay", "Income payment day"))
            add(date("incomeStart", "Income start"))
            add(date("incomeEnd", "Income end"))
        }
        "REAL_ESTATE" -> listOf(number("areaSqFt", "Area in square feet"),
            choice("ownership", "Ownership", "SELF", "JOINT", "HUF", "SPOUSE"),
            number("ownershipPercent", "Recorded ownership percent"),
            choice("altHoldMode", "Alternative holding mode", "DEMAT", "LUMPSUM"),
            money("monthlyIncomeMinor", "Monthly distribution ₹"),
            integer("incomeDay", "Income payment day"),
            date("incomeStart", "Income start"), date("incomeEnd", "Income end"))
        "SPECIALISED" -> listOf(
            choice("aifCategory", "AIF category", "I", "II", "III"),
            money("commitmentMinor", "Investment commitment ₹"),
            money("capitalCalledMinor", "Capital called ₹"),
            text("strategyName", "Investment strategy"),
            text("custodian", "Custodian"),
            choice("altHoldMode", "Alternative holding mode", "DEMAT", "LUMPSUM"))
        "CASH_BANK" -> listOf(text("ifsc", "IFSC"))
        "LIABILITY" -> listOf(integer("originalTenorMonths", "Original tenor (months)"),
            choice("rateType", "Loan interest rate", "FLOATING", "FIXED"),
            text("linkedHoldingId", "Linked holding identifier"))
        else -> emptyList()
    }

    fun read(json: String?, category: String, subcategory: String): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        val data = JSONObject(json)
        return fields(category, subcategory).mapNotNull { field ->
            if (!data.has(field.key) || data.isNull(field.key)) null
            else {
                val value = when (field.kind) {
                    Kind.MONEY -> NativeBooksRepository.formatMinor(
                        data.getString(field.key).toLong(), 4)
                    Kind.BOOLEAN -> data.getBoolean(field.key).toString()
                    else -> data.get(field.key).toString()
                }
                field.key to value
            }
        }.toMap()
    }

    /** Unspecified fields stay unchanged; explicitly blank visible fields are removed. */
    fun merge(original: String?, category: String, subcategory: String,
        input: Map<String, String>): String? {
        val fields = fields(category, subcategory).associateBy { it.key }
        require(input.keys.all { it in fields }) { "Field does not belong to this asset category" }
        val data = if (original.isNullOrBlank()) JSONObject() else JSONObject(original)
        input.forEach { (key, raw) ->
            val field = fields.getValue(key)
            val value = raw.trim()
            if (value.isBlank()) {
                data.remove(key)
            } else {
                require(value.length <= 256 && value.none { it.isISOControl() }) {
                    "Invalid specialist value"
                }
                when (field.kind) {
                    Kind.ENUM -> {
                        require(value in field.options) { "Unsupported specialist option" }
                        data.put(key, value)
                    }
                    Kind.TEXT -> data.put(key, value)
                    Kind.DATE -> {
                        require(LocalDate.parse(value).toString() == value)
                        data.put(key, value)
                    }
                    Kind.MONEY -> {
                        val minor = NativeBooksRepository.parseMinor(value, 4)
                        require(minor >= 0L) { "Negative specialist amount" }
                        data.put(key, minor.toString())
                    }
                    Kind.DECIMAL -> {
                        val number = BigDecimal(value)
                        require(number >= BigDecimal.ZERO && number <= BigDecimal("1000000000"))
                        if (key == "ownershipPercent") require(number <= BigDecimal("100"))
                        data.put(key, number.toDouble())
                    }
                    Kind.INTEGER -> {
                        val integer = value.toInt()
                        require(integer >= 0 && (key != "incomeDay" || integer in 1..31))
                        data.put(key, integer)
                    }
                    Kind.BOOLEAN -> {
                        require(value == "true" || value == "false")
                        data.put(key, value == "true")
                    }
                }
            }
        }
        val result = data.toString()
        require(result.length <= 32768) { "Specialist metadata too large" }
        return if (data.length() == 0) null else result
    }
}
