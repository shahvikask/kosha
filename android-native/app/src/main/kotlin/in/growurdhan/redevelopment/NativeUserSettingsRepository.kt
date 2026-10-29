package `in`.growurdhan.redevelopment

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.math.BigDecimal
import java.time.LocalDate

/** Explicit P31 preferences in the authenticated SQLCipher v9 setting table. */
internal class NativeUserSettingsRepository(private val accounts: NativeAccountRepository) {
    enum class Appearance { SYSTEM, LIGHT, DARK;
        fun useDark(systemDark: Boolean): Boolean = when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }
    }
    enum class SgbReference { RECORDED_MARKET_CLOSE, RBI_REFERENCE_REQUIRED }
    enum class IbjaReference { DISABLED, OWNER_REVIEW_REQUIRED }
    enum class PortfolioSort(val holdingSort: String) {
        NAME("Name"), RECORDED_VALUE("Recorded value"),
        RECENT_PRICE("Recent price"), CATEGORY("Category")
    }
    enum class ArchiveDefault(val holdingStatus: String) {
        ALL("All"), ACTIVE("Active"), ARCHIVED("Archived")
    }
    enum class HomePreview(val maxRows: Int) {
        COMPACT(6), EXTENDED(12)
    }
    data class Preferences(
        val appearance: Appearance = Appearance.SYSTEM,
        val sgbReference: SgbReference = SgbReference.RECORDED_MARKET_CLOSE,
        val ibjaReference: IbjaReference = IbjaReference.DISABLED,
        val portfolioSort: PortfolioSort = PortfolioSort.NAME,
        val archiveDefault: ArchiveDefault = ArchiveDefault.ALL,
        val homePreview: HomePreview = HomePreview.EXTENDED
    )
    /** Owner-entered claim, NOT a verified IBJA rate or a portfolio price. */
    data class IbjaInput(val asOf: String, val rupeesPerTenGramsMinor: Long) {
        fun displayRate(): String = NativeBooksRepository.formatMinor(rupeesPerTenGramsMinor, 4)
    }
    private val ibjaInputKey = "native.prices.ibja.owner_reference"
    private val portfolioSortKey = "native.portfolio.default_sort"
    private val archiveDefaultKey = "native.portfolio.archive_filter"
    private val homePreviewKey = "native.portfolio.home_rows"
    private val appearanceKey = "native.privacy.appearance"
    private val sgbKey = "native.prices.sgb.reference"
    private val ibjaKey = "native.prices.ibja.reference"

    private fun get(db: SQLiteDatabase, key: String): String? =
        db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun load(db: SQLiteDatabase): Preferences = Preferences(
        appearance = get(db, appearanceKey)?.let(Appearance::valueOf) ?: Appearance.SYSTEM,
        sgbReference = get(db, sgbKey)?.let(SgbReference::valueOf)
            ?: SgbReference.RECORDED_MARKET_CLOSE,
        ibjaReference = get(db, ibjaKey)?.let(IbjaReference::valueOf)
            ?: IbjaReference.DISABLED,
        portfolioSort = get(db, portfolioSortKey)?.let(PortfolioSort::valueOf)
            ?: PortfolioSort.NAME,
        archiveDefault = get(db, archiveDefaultKey)?.let(ArchiveDefault::valueOf)
            ?: ArchiveDefault.ALL,
        homePreview = get(db, homePreviewKey)?.let(HomePreview::valueOf)
            ?: HomePreview.EXTENDED
    )
    fun read(email: String): Preferences = accounts.withUnlockedVault(email) { load(it) }

    private fun validated(date: String, amount: String): IbjaInput {
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Invalid reference date" }
        val day = LocalDate.parse(date)
        require(day.toString() == date && !day.isAfter(LocalDate.now())) {
            "Future or noncanonical IBJA date"
        }
        require(amount.matches(Regex("(0|[1-9][0-9]{0,9})(\\.[0-9]{1,4})?"))) {
            "Invalid positive rupee rate"
        }
        val minor = BigDecimal(amount).movePointRight(4).longValueExact()
        require(minor > 0L) { "IBJA reference rate must be positive" }
        return IbjaInput(date, minor)
    }

    private fun decoded(value: String): IbjaInput {
        val pieces = value.split("|")
        require(pieces.size == 2 && pieces[1].matches(Regex("[1-9][0-9]*"))) {
            "Invalid encrypted IBJA reference"
        }
        val rate = pieces[1].toLong()
        val v = validated(pieces[0], BigDecimal.valueOf(rate, 4).toPlainString())
        require(v.rupeesPerTenGramsMinor == rate &&
            value == "${v.asOf}|${v.rupeesPerTenGramsMinor}") {
            "Noncanonical IBJA reference"
        }
        return v
    }

    fun readIbja(email: String): IbjaInput? = accounts.withUnlockedVault(email) { db ->
        get(db, ibjaInputKey)?.let(::decoded)
    }

    /** Saves only an owner claim, never calls a feed or changes a holding value. */
    fun saveIbja(email: String, date: String, amount: String): IbjaInput {
        val input = validated(date, amount)
        accounts.withUnlockedVault(email) { db ->
            require(load(db).ibjaReference == IbjaReference.OWNER_REVIEW_REQUIRED) {
                "IBJA owner-review preference required"
            }
            db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(ibjaInputKey,
                    "${input.asOf}|${input.rupeesPerTenGramsMinor}"))
        }
        return input
    }

    fun clearIbja(email: String) {
        accounts.withUnlockedVault(email) { db ->
            db.execSQL("DELETE FROM setting WHERE key=?", arrayOf(ibjaInputKey))
        }
    }

    /** Atomic preference update after fresh vault/device admission, never plaintext. */
    fun save(email: String, preferences: Preferences) {
        accounts.withUnlockedVault(email) { db ->
            for ((key, value) in listOf(
                appearanceKey to preferences.appearance.name,
                sgbKey to preferences.sgbReference.name,
                ibjaKey to preferences.ibjaReference.name,
                portfolioSortKey to preferences.portfolioSort.name,
                archiveDefaultKey to preferences.archiveDefault.name,
                homePreviewKey to preferences.homePreview.name
            )) db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(key, value))
        }
    }
}
