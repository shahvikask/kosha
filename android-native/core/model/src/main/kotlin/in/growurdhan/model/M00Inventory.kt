package `in`.growurdhan.model

data class RouteParity(
    val legacySurface: String,
    val nativeDestination: String,
    val implementedInM00: Boolean,
)

data class HoldingField(val key: String, val valueKind: String)

/**
 * Legacy surfaces from the M00 inventory and the native destination that will
 * own them. A destination is implemented here only when a concept screen exists.
 * Unimplemented destinations are not rendered as tappable controls.
 */
object M00Inventory {
    val routes: List<RouteParity> = listOf(
        RouteParity("Licence wait", "licence/checking", false),
        RouteParity("Closed", "terms/closed", false),
        RouteParity("Database error", "vault/error", false),
        RouteParity("Onboarding", "onboarding", false),
        RouteParity("Licence", "licence", false),
        RouteParity("Auth", "unlock", true),
        RouteParity("Home", "home", true),
        RouteParity("Holdings", "portfolio", false),
        RouteParity("Accounts", "portfolio/accounts", false),
        RouteParity("Documents", "statements", false),
        RouteParity("Family", "family", false),
        RouteParity("CAS review", "statements/review", false),
        RouteParity("Feedback", "feedback", false),
        RouteParity("Terms", "terms", false),
        RouteParity("Add holding", "portfolio/add", false),
        RouteParity("Add record", "ledger/add", false),
        RouteParity("Holding detail", "portfolio/holding", true),
        RouteParity("Emergency sheet", "family/emergency", false),
        RouteParity("Brand", "design/lockup", true),
        RouteParity("Errors", "app/error", false),
        RouteParity("Privacy", "privacy", false),
        RouteParity("Charts", "home/chart", false),
        RouteParity("Header", "app/top-bar", true),
        RouteParity("Sidebar", "app/navigation", false),
        RouteParity("Status bar", "app/status", false),
    )

    val holdingFields: List<HoldingField> = listOf(
        HoldingField("goldHoldMode", "GoldHoldMode"),
        HoldingField("goldPurity", "GoldPurity"),
        HoldingField("goldHallmark", "string"),
        HoldingField("earlyExitDate", "string"),
        HoldingField("annualContributionMinor", "string"),
        HoldingField("contributionOn", "string"),
        HoldingField("compounding", "Compounding"),
        HoldingField("tdsApplicable", "boolean"),
        HoldingField("npsTier", "NpsTier"),
        HoldingField("npsCra", "NpsCra"),
        HoldingField("npsPfm", "string"),
        HoldingField("faceValueMinor", "string"),
        HoldingField("areaSqFt", "number"),
        HoldingField("ownership", "OwnershipKind"),
        HoldingField("ownershipPercent", "number"),
        HoldingField("aifCategory", "AifCategory"),
        HoldingField("commitmentMinor", "string"),
        HoldingField("capitalCalledMinor", "string"),
        HoldingField("ifsc", "string"),
        HoldingField("strategyName", "string"),
        HoldingField("custodian", "string"),
        HoldingField("originalTenorMonths", "number"),
        HoldingField("rateType", "RateType"),
        HoldingField("linkedHoldingId", "string"),
        HoldingField("mfPlan", "MfPlan"),
        HoldingField("mfOption", "MfOption"),
        HoldingField("mfHeldAs", "MfHeldAs"),
        HoldingField("depository", "Depository"),
        HoldingField("dpId", "string"),
        HoldingField("clientId", "string"),
        HoldingField("boId", "string"),
        HoldingField("altHoldMode", "AltHoldMode"),
        HoldingField("monthlyIncomeMinor", "string"),
        HoldingField("incomeDay", "number"),
        HoldingField("incomeStart", "string"),
        HoldingField("incomeEnd", "string"),
    )

    val conceptScreens = listOf("unlock", "home", "portfolio/holding")
}
