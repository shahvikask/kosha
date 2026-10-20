package `in`.growurdhan.redevelopment

/**
 * Strict lexical admission check for already AES-GCM-authenticated legacy JSON.
 *
 * Android org.json is deliberately lenient: it can accept non-JSON syntax,
 * and historical parsers may collapse duplicate object keys. For a financial
 * restore that would silently discard original signed fields. Run this before
 * JSONObject, without rewriting, serializing, or logging the source.
 *
 * Bounded by the upstream 64 MiB plaintext limit and MAX_DEPTH. Duplicate
 * keys compare decoded Unicode strings, not raw JSON escape spellings.
 */
internal object AuthenticatedJsonStructure {
    private const val MAX_DEPTH = 80

    fun requireUnambiguous(raw: String) {
        val parser = Reader(raw)
        parser.whitespace()
        require(parser.current() == '{') { "Authenticated snapshot must be a JSON object" }
        parser.value(0)
        parser.whitespace()
        require(parser.atEnd()) { "Trailing data after authenticated JSON" }
    }

    private class Reader(private val source: String) {
        private var offset = 0

        fun atEnd(): Boolean = offset == source.length
        fun current(): Char? = source.getOrNull(offset)
        private fun fail(): Nothing =
            throw IllegalArgumentException("Malformed or ambiguous authenticated JSON")

        fun whitespace() {
            while (true) {
                when (current()) {
                    ' ', '\n', '\r', '\t' -> offset++
                    else -> return
                }
            }
        }

        private fun take(ch: Char) {
            if (current() != ch) fail()
            offset++
        }

        private fun digit(): Boolean = current()?.let { it in '0'..'9' } == true

        fun value(depth: Int) {
            if (depth > MAX_DEPTH) fail()
            whitespace()
            when (current()) {
                '{' -> obj(depth + 1)
                '[' -> array(depth + 1)
                '"' -> quoted(decode = false)
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                '-' -> number()
                else -> if (current()?.let { it in '0'..'9' } == true) number() else fail()
            }
        }

        private fun literal(expected: String) {
            for (character in expected) take(character)
        }

        private fun number() {
            if (current() == '-') offset++
            val first = current()
            when {
                first == '0' -> offset++
                first != null && first in '1'..'9' -> while (digit()) offset++
                else -> fail()
            }
            if (current() == '.') {
                offset++
                if (!digit()) fail()
                while (digit()) offset++
            }
            if (current() == 'e' || current() == 'E') {
                offset++
                if (current() == '+' || current() == '-') offset++
                if (!digit()) fail()
                while (digit()) offset++
            }
        }

        private fun obj(depth: Int) {
            take('{')
            whitespace()
            if (current() == '}') { offset++; return }
            val keys = HashSet<String>()
            while (true) {
                if (current() != '"') fail()
                val key = requireNotNull(quoted(decode = true))
                if (!keys.add(key)) fail()
                whitespace()
                take(':')
                value(depth)
                whitespace()
                when (current()) {
                    '}' -> { offset++; return }
                    ',' -> { offset++; whitespace() }
                    else -> fail()
                }
            }
        }

        private fun array(depth: Int) {
            take('[')
            whitespace()
            if (current() == ']') { offset++; return }
            while (true) {
                value(depth)
                whitespace()
                when (current()) {
                    ']' -> { offset++; return }
                    ',' -> offset++
                    else -> fail()
                }
            }
        }

        private fun unicodeCharacter(): Char {
            var number = 0
            repeat(4) {
                val ch = current() ?: fail()
                val hex = ch.digitToIntOrNull(16) ?: fail()
                number = (number shl 4) or hex
                offset++
            }
            return number.toChar()
        }

        private fun quoted(decode: Boolean): String? {
            take('"')
            val output = if (decode) StringBuilder() else null
            var pendingSurrogate = false
            fun record(ch: Char) {
                when {
                    ch.isHighSurrogate() -> {
                        if (pendingSurrogate) fail()
                        pendingSurrogate = true
                    }
                    ch.isLowSurrogate() -> {
                        if (!pendingSurrogate) fail()
                        pendingSurrogate = false
                    }
                    pendingSurrogate -> fail()
                }
                output?.append(ch)
            }
            while (true) {
                val char = current() ?: fail()
                offset++
                when (char) {
                    '"' -> {
                        if (pendingSurrogate) fail()
                        return output?.toString()
                    }
                    '\\' -> {
                        val escaped = current() ?: fail()
                        offset++
                        val decoded = when (escaped) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'
                            'f' -> '\u000c'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> unicodeCharacter()
                            else -> fail()
                        }
                        record(decoded)
                    }
                    else -> {
                        if (char.code < 0x20) fail()
                        record(char)
                    }
                }
            }
        }
    }
}
