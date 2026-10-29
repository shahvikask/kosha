package `in`.growurdhan.redevelopment

import net.zetetic.database.sqlcipher.SQLiteDatabase

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
    data class Preferences(
        val appearance: Appearance = Appearance.SYSTEM,
        val sgbReference: SgbReference = SgbReference.RECORDED_MARKET_CLOSE,
        val ibjaReference: IbjaReference = IbjaReference.DISABLED
    )
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
            ?: IbjaReference.DISABLED
    )
    fun read(email: String): Preferences = accounts.withUnlockedVault(email) { load(it) }

    /** Atomic preference update after fresh vault/device admission, never plaintext. */
    fun save(email: String, preferences: Preferences) {
        accounts.withUnlockedVault(email) { db ->
            for ((key, value) in listOf(
                appearanceKey to preferences.appearance.name,
                sgbKey to preferences.sgbReference.name,
                ibjaKey to preferences.ibjaReference.name
            )) db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
                arrayOf<Any>(key, value))
        }
    }
}
