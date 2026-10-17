import `in`.growurdhan.database.LegacyBackupEnvelopeReader
import `in`.growurdhan.database.RestoreIdentityGate
import java.io.File
import java.security.MessageDigest

/** Developer-only JCE check of bytes emitted by the exact frozen TypeScript exporters. */
fun main(args: Array<String>) {
    val root = File(args.single())
    val lines = File(root, "fields.txt").readLines()
    require(lines.size == 17)
    val original = File(root, "snapshot.json").readText()
    val originalSha = File(root, "snapshot.sha256").readText().trim()
    val actualSha = MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
        .joinToString("") { "%02x".format(it) }
    check(actualSha == originalSha) { "Original JSON digest mismatch" }
    val email = lines[6]
    val password = "m24-fixture-password-not-a-secret"
    val binary = File(root, "modern.kosha").readBytes()
    check(LegacyBackupEnvelopeReader.decodeGUDVLT01(binary, email, password) == original) {
        "Actual GUDVLT01 frozen exporter is incompatible with Kotlin JCE decoder"
    }
    val legacy = LegacyBackupEnvelopeReader.Kosha1(lines[0], lines[1],
        lines[2].toInt(), lines[3], lines[4], lines[5])
    check(LegacyBackupEnvelopeReader.decodeKOSHA1(legacy, password) == original) {
        "Actual KOSHA1 frozen exporter is incompatible with Kotlin JCE decoder"
    }
    val account = RestoreIdentityGate.Account(lines[6], lines[7], lines[8],
        lines[9].toInt(), lines[10], lines[11], lines[12], lines[13],
        lines[14], lines[15], lines[16])
    val valid = RestoreIdentityGate.Credentials(email, password, "ABCDE1234F",
        "1990-01-01", "city", "first city", "pet", "blue sea")
    RestoreIdentityGate.verifyForNewDevice(account, valid)
    var rejected = 0
    fun reject(block: () -> Unit) {
        try { block() } catch (_: Exception) { rejected++; return }
        error("Invalid fixture identity/envelope unexpectedly accepted")
    }
    reject { LegacyBackupEnvelopeReader.decodeGUDVLT01(binary, "wrong@example.invalid", password) }
    reject { LegacyBackupEnvelopeReader.decodeGUDVLT01(binary, email, "wrong-password") }
    val tampered = binary.clone()
    tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
    reject { LegacyBackupEnvelopeReader.decodeGUDVLT01(tampered, email, password) }
    reject { LegacyBackupEnvelopeReader.decodeKOSHA1(legacy, "wrong-password") }
    reject { RestoreIdentityGate.verifyForNewDevice(account, valid.copy(password = "wrong")) }
    reject { RestoreIdentityGate.verifyForNewDevice(account, valid.copy(q1Answer = "wrong")) }
    reject { RestoreIdentityGate.verifyForNewDevice(account, valid.copy(pan = "ABCDE0000G")) }
    check(rejected == 7 && original.contains("9007199254740993") &&
        original.contains("1234567890123") && original.contains("unknownNested") &&
        original.contains("owner-A")) { "Producer snapshot lost opaque/scaled content" }
    println("M24_FROZEN_EXPORTER_TO_JCE_PASS formats=2 recovery=1 negative=7 plaintextSha=$actualSha")
}
