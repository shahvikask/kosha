package `in`.growurdhan.app.presentation

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic ciphertext headers only. Crypto/authentication lives in VaultBackupCodec. */
class PortableBackupBytesTest {
    private fun header(size: Int = 128): ByteArray =
        ByteArray(size).also {
            "GUDNBACK".toByteArray(Charsets.US_ASCII).copyInto(it)
        }

    @Test fun acceptsOnlyBoundedBackupHeaderWithoutInterpretingItAsPlaintext() {
        val bytes = header()
        val actual = PortableBackupBytes.read(ByteArrayInputStream(bytes), maxBytes = 512)
        assertArrayEquals(bytes, actual)
    }

    @Test fun rejectsNonBackupFileAndShortTruncatedHeader() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupBytes.read(ByteArrayInputStream(ByteArray(128)), maxBytes = 512)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupBytes.read(ByteArrayInputStream(header(size = 63)), maxBytes = 512)
        }
    }

    @Test fun rejectsOversizedInputEvenWithValidPrefix() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupBytes.read(ByteArrayInputStream(header(size = 513)), maxBytes = 512)
        }
    }

    @Test fun rejectsContentProvidersThatRepeatedlyReturnZero() {
        val stream = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupBytes.read(stream, maxBytes = 512)
        }
    }

    @Test fun readerCannotBeConfiguredBeyondProductionMaximum() {
        assertTrue(PortableBackupBytes.MAX_CIPHERTEXT_BYTES > 32 * 1024 * 1024)
        assertThrows(IllegalArgumentException::class.java) {
            PortableBackupBytes.read(
                ByteArrayInputStream(header()),
                maxBytes = PortableBackupBytes.MAX_CIPHERTEXT_BYTES + 1,
            )
        }
    }
}
