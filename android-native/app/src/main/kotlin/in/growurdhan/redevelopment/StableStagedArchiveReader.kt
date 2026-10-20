package `in`.growurdhan.redevelopment

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Read-only staged GUDVLT01/KOSHA1 archive loader. Android AtomicFile.openRead
 * may reconcile *.bak (and mutate evidence); an interrupted staging write
 * instead requires separate explicit owner-reviewed recovery. This class
 * NEVER deletes, renames, truncates or auto-repairs staged financial data.
 */
internal object StableStagedArchiveReader {
    private const val MAX_ARCHIVE = 64 * 1024 * 1024
    private const val MIN_ARCHIVE = 56

    fun read(base: Path): ByteArray {
        val path = base.toAbsolutePath().normalize()
        val directory = path.parent ?: error("Missing private restore directory")
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(directory)) {
            "Unsafe staged restore directory"
        }
        for (suffix in listOf(".new", ".bak")) {
            require(!Files.exists(directory.resolve(path.fileName.toString() + suffix),
                LinkOption.NOFOLLOW_LINKS)) {
                "Unfinished AtomicFile restore staging requires owner review"
            }
        }
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(path)) { "No safe staged encrypted backup" }
        val before = Files.readAttributes(path, BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        require(before.size() in MIN_ARCHIVE.toLong()..MAX_ARCHIVE.toLong()) {
            "Truncated or oversized staged encrypted backup"
        }
        val bytes = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS).use { input ->
            ByteArrayOutputStream().use { out ->
                val chunk = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    total = Math.addExact(total, count.toLong())
                    require(total <= MAX_ARCHIVE) { "Oversized staged backup" }
                    out.write(chunk, 0, count)
                }
                require(total == before.size()) {
                    "Staged encrypted archive changed during read"
                }
                out.toByteArray()
            }
        }
        val after = Files.readAttributes(path, BasicFileAttributes::class.java,
            LinkOption.NOFOLLOW_LINKS)
        require(after.isRegularFile && after.size() == before.size() &&
            after.lastModifiedTime() == before.lastModifiedTime() &&
            (before.fileKey() == null || before.fileKey() == after.fileKey()) &&
            !Files.exists(directory.resolve(path.fileName.toString() + ".bak"),
                LinkOption.NOFOLLOW_LINKS) &&
            !Files.exists(directory.resolve(path.fileName.toString() + ".new"),
                LinkOption.NOFOLLOW_LINKS)) {
            "Staged encrypted archive was replaced or concurrently modified"
        }
        return bytes
    }
}
