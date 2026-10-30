package `in`.growurdhan.model

/**
 * E18 bounded source-class inventory; reviewed by enterprise evidence gate.
 * External traffic is not proof of identity or consent. No household backend.
 */
object NativeEgressInventory {
    enum class Destination { NSE_OFFICIAL_CLOSE, BSE_OFFICIAL_CLOSE,
        AMFI_OFFICIAL_NAV, PLAY_BILLING_SERVICE }
    data class Route(val destination: Destination, val mechanism: String,
        val allowedHost: String?, val personalFinancialPayload: Boolean)
    val entries = listOf(
        Route(Destination.NSE_OFFICIAL_CLOSE, "Manual HTTPS archive GET",
            "nsearchives.nseindia.com", false),
        Route(Destination.BSE_OFFICIAL_CLOSE, "Manual HTTPS archive GET",
            "www.bseindia.com", false),
        Route(Destination.AMFI_OFFICIAL_NAV, "Manual HTTPS NAV catalog GET",
            "www.amfiindia.com", false),
        Route(Destination.PLAY_BILLING_SERVICE, "User-initiated Android Play Billing SDK",
            null, false)
    )
    init {
        require(entries.map { it.destination }.distinct().size == entries.size)
        require(entries.none { it.personalFinancialPayload })
    }
}
