package `in`.growurdhan.core.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** Book values are 1/10,000 INR; stored in SQLite INTEGER, never REAL. */
@JvmInline value class MoneyScaled(val raw: Long) {
    operator fun plus(other: MoneyScaled) = MoneyScaled(Math.addExact(raw, other.raw))
    operator fun minus(other: MoneyScaled) = MoneyScaled(Math.subtractExact(raw, other.raw))
    fun rupees(): BigDecimal = BigDecimal.valueOf(raw, 4)
    companion object {
        fun fromRupees(amount: BigDecimal): MoneyScaled =
            MoneyScaled(amount.setScale(4, RoundingMode.HALF_UP).movePointRight(4).longValueExact())
    }
}

/** Asset units stored at eight decimal places. */
@JvmInline value class QuantityScaled(val raw: Long) {
    init { require(raw >= 0) { "Asset quantities cannot be negative." } }
    fun units(): BigDecimal = BigDecimal.valueOf(raw, 8)
    companion object {
        fun fromUnits(units: BigDecimal) =
            QuantityScaled(units.setScale(8, RoundingMode.HALF_UP).movePointRight(8).longValueExact())
    }
}

/** Checked multiplication with explicit half-away-from-zero rounding at the scaled-integer boundary. */
fun marketValue(quantity: QuantityScaled, unitPrice: MoneyScaled): MoneyScaled {
    val scale = BigInteger.valueOf(100_000_000L)
    val (whole, rem) = BigInteger.valueOf(quantity.raw)
        .multiply(BigInteger.valueOf(unitPrice.raw))
        .divideAndRemainder(scale)
    val rounded = if (rem.abs().shiftLeft(1) >= scale) {
        whole.add(BigInteger.valueOf(if (unitPrice.raw >= 0) 1 else -1))
    } else whole
    return MoneyScaled(rounded.longValueExact())
}

@JvmInline value class OwnerId(val raw: String) {
    init { require(raw.isNotBlank() && raw.length <= 128) { "Invalid owner identifier." } }
}
@JvmInline value class HoldingId(val raw: String) {
    init { require(raw.isNotBlank() && raw.length <= 128) { "Invalid holding identifier." } }
}

data class FamilyMember(
    val id: OwnerId,
    val displayName: String,
    val relationship: String,
    val isPrimary: Boolean,
) {
    init {
        require(displayName.isNotBlank() && displayName.length <= 200)
        require(relationship.isNotBlank() && relationship.length <= 32)
    }
}

data class CustodyAccount(
    val id: String,
    val ownerId: OwnerId,
    val accountType: String,
    val identifierMasked: String,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(accountType.isNotBlank() && accountType.length <= 40)
        require(identifierMasked.length <= 200)
    }
}

data class Holding(
    val id: HoldingId,
    val ownerId: OwnerId,
    val accountId: String?,
    val name: String,
    val assetCategory: String,
    val quantity: QuantityScaled,
    val currentUnitPrice: MoneyScaled,
) {
    init {
        require(name.isNotBlank() && name.length <= 300)
        require(assetCategory.isNotBlank() && assetCategory.length <= 50)
        require(accountId == null || accountId.isNotBlank() && accountId.length <= 128)
    }
}

enum class LedgerKind {
    BUY, SELL, DIVIDEND, INTEREST, OPENING_BALANCE, ADJUSTMENT, CORRECTION,
}

data class LedgerEvent(
    val id: String,
    val holdingId: HoldingId,
    val ownerId: OwnerId,
    val kind: LedgerKind,
    val eventDate: String,
    val quantityDeltaScaled: Long,
    val amount: MoneyScaled,
    val sourceFingerprint: String?,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(eventDate))
        require(sourceFingerprint == null || sourceFingerprint.length <= 128)
    }
}

/** An observation, not a BUY or other transaction. */
data class StatementPosition(
    val holdingId: HoldingId,
    val quantity: QuantityScaled,
    val value: MoneyScaled,
)

data class StatementSnapshot(
    val id: String,
    val ownerId: OwnerId,
    val sha256: String,
    val asOf: String,
    val complete: Boolean,
    val positions: List<StatementPosition>,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(Regex("^[0-9a-f]{64}$").matches(sha256)) { "Requires a SHA-256 fingerprint." }
        require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(asOf))
    }
}

enum class SnapshotResult { IMPORTED, ALREADY_IMPORTED }

data class VaultStatus(
    val initialized: Boolean,
    val opened: Boolean,
    val schemaVersion: Int,
)

data class VaultHealth(
    val integrity: String,
    val foreignKeyViolations: Int,
    val memberCount: Int,
    val holdingCount: Int,
    val eventCount: Int,
)
