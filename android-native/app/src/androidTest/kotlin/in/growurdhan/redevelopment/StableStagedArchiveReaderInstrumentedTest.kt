package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic opaque encrypted-looking bytes. Tests non-mutating, fail-closed
 * staging file semantics, not actual customer backups or cryptographic
 * restoration acceptance.
 */
@RunWith(AndroidJUnit4::class)
class StableStagedArchiveReaderInstrumentedTest {
    private fun directory(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.cacheDir, "stage-reader-" + UUID.randomUUID())
            .also { assertTrue(it.mkdirs()) }
    }
    private fun archive() = ByteArray(112) { index -> (index * 23).toByte() }

    @Test fun cleanStagedArchiveCanBeInspectedWithoutChangingItsBytes() {
        val file = File(directory(), "restore-pending.v1.kosha")
        val original = archive()
        file.writeBytes(original)
        val modified = file.lastModified()
        assertArrayEquals(original, StableStagedArchiveReader.read(file.toPath()))
        assertArrayEquals(original, file.readBytes())
        assertEquals(modified, file.lastModified())
        assertFalse(File(file.path + ".bak").exists())
        assertFalse(File(file.path + ".new").exists())
    }

    @Test fun atomicBackupIsNeverSilentlyRecoveredOrDeleted() {
        val staged = File(directory(), "restore-pending.v1.kosha")
        val original = archive()
        staged.writeBytes(original)
        val backup = File(staged.path + ".bak")
        backup.writeBytes(original.reversedArray())
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(staged.toPath())
        }
        assertArrayEquals(original, staged.readBytes())
        assertArrayEquals(original.reversedArray(), backup.readBytes())

        // Backups without a corresponding canonical base file are forensic
        // evidence, not permission to promote an older archive automatically.
        assertTrue(staged.delete())
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(staged.toPath())
        }
        assertArrayEquals(original.reversedArray(), backup.readBytes())
    }

    @Test fun unfinishedAtomicNewFileIsNeverDroppedByReadOnlyInspection() {
        val staged = File(directory(), "restore-pending.v1.kosha")
        val original = archive()
        staged.writeBytes(original)
        val unfinished = File(staged.path + ".new")
        val partial = byteArrayOf(1, 2, 3, 4, 5)
        unfinished.writeBytes(partial)
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(staged.toPath())
        }
        assertArrayEquals(partial, unfinished.readBytes())
        assertArrayEquals(original, staged.readBytes())
    }

    @Test fun symlinkStagingAndTruncatedOrOversizedFilesFailClosed() {
        val base = directory()
        val real = File(base, "real-backup.bin")
        real.writeBytes(archive())
        val linked = File(base, "restore-pending.v1.kosha")
        Files.createSymbolicLink(linked.toPath(), real.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(linked.toPath())
        }
        assertArrayEquals(archive(), real.readBytes())

        val short = File(base, "short")
        short.writeBytes(ByteArray(55))
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(short.toPath())
        }

        val huge = File(base, "oversized")
        RandomAccessFile(huge, "rw").use { it.setLength(64L * 1024 * 1024 + 1) }
        assertThrows(IllegalArgumentException::class.java) {
            StableStagedArchiveReader.read(huge.toPath())
        }
    }
}
