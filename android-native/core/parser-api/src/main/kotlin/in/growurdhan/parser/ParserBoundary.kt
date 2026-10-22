package `in`.growurdhan.parser

/**
 * Pure Kotlin CAS admission checks. This module never loads the frozen Python parser.
 * Passing this gate does not mean a statement is genuine, parsed, or importable.
 */
object ParserBoundary {
    const val CHAQUOPY_LINKED = false
    const val CALLS_FROZEN_PARSER = false

    enum class Format { PDF, CSV }
    enum class Refusal { EMPTY, TOO_LARGE, BAD_SIGNATURE, BINARY_CSV, UNSUPPORTED_FORMAT }
    sealed interface Admission {
        data class Allowed(val format: Format) : Admission
        data class Rejected(val reason: Refusal) : Admission
    }

    // A conservative admission cap, not the parser's memory or processing budget.
    const val MAX_INPUT_BYTES = 25 * 1024 * 1024

    /**
     * Non-consuming, fail-closed format check. Does not persist, transform, or log
     * customer financial data. Content integrity and issuer identity are separate gates.
     */
    fun admit(fileName: String, bytes: ByteArray): Admission {
        if (bytes.isEmpty()) return Admission.Rejected(Refusal.EMPTY)
        if (bytes.size > MAX_INPUT_BYTES) return Admission.Rejected(Refusal.TOO_LARGE)
        return when (fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase(java.util.Locale.ROOT)) {
            "pdf" -> {
                val pdfMagic = byteArrayOf(37, 80, 68, 70, 45) // %PDF-
                if (bytes.size >= pdfMagic.size && pdfMagic.indices.all { bytes[it] == pdfMagic[it] }) {
                    Admission.Allowed(Format.PDF)
                } else Admission.Rejected(Refusal.BAD_SIGNATURE)
            }
            "csv" -> {
                // Reject NUL and invalid UTF-8 rather than silently changing financial text.
                if (bytes.any { it == 0.toByte() }) return Admission.Rejected(Refusal.BINARY_CSV)
                val decoder = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                try {
                    decoder.decode(java.nio.ByteBuffer.wrap(bytes))
                    Admission.Allowed(Format.CSV)
                } catch (_: java.nio.charset.CharacterCodingException) {
                    Admission.Rejected(Refusal.BINARY_CSV)
                }
            }
            else -> Admission.Rejected(Refusal.UNSUPPORTED_FORMAT)
        }
    }
}
