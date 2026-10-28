package `in`.growurdhan.model

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** P11: cryptographically verified source rows, never user-entered title or appraisal.
 * No production issuer roots are shipped until approved independently.
 */
object NativePropertySignedEvidence {
    data class TrustRoots(val registrarEd25519: Map<String, ByteArray>,
        val appraiserEd25519: Map<String, ByteArray>)
    val productionRoots = TrustRoots(emptyMap(), emptyMap())

    data class Proof(
        val claim: NativeSharedPropertyAggregation.Claim,
        val custodyId: String,
        val registrarId: String,
        val appraiserId: String,
        val registrySourceBytes: ByteArray,
        val registrySignature: ByteArray,
        val valuationSourceBytes: ByteArray,
        val valuationDigest: String,
        val valuationSignature: ByteArray
    )

    private val ed25519Provider = BouncyCastleProvider()
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun valid(s: String) = s.isNotBlank() && s.length <= 160 &&
        s.none(Char::isISOControl)
    private fun signedMessage(domain: String, fields: List<String>): ByteArray =
        ByteArrayOutputStream().use { sink ->
            DataOutputStream(sink).use { out ->
                out.writeUTF(domain)
                fields.forEach { out.writeUTF(it) }
            }
            sink.toByteArray()
        }

    fun registryMessage(p: Proof): ByteArray = signedMessage("GUD-P11-REGISTRY-1",
        listOf(p.claim.holdingId, p.claim.ownerId, p.custodyId,
            p.claim.registeredPropertyId, p.claim.identitySourceRef,
            p.claim.sourceDigest, p.claim.sourceAsOf.toString(), p.claim.memberPercent))
    fun appraisalMessage(p: Proof): ByteArray = signedMessage("GUD-P11-APPRAISAL-1",
        listOf(p.claim.registeredPropertyId, p.claim.valuationSourceRef,
            p.valuationDigest, p.claim.valuationAsOf.toString(),
            p.claim.grossPropertyValueMinor.toString()))

    private fun verifySignature(key: ByteArray, payload: ByteArray, signature: ByteArray) {
        require(signature.size == 64 && key.isNotEmpty()) { "Missing issuer signature" }
        val publicKey = KeyFactory.getInstance("Ed25519", ed25519Provider)
            .generatePublic(X509EncodedKeySpec(key))
        require(Signature.getInstance("Ed25519", ed25519Provider).run {
            initVerify(publicKey)
            update(payload)
            verify(signature)
        }) { "Untrusted or mismatched source signature" }
    }

    fun verify(p: Proof, roots: TrustRoots, asOf: LocalDate): NativeSharedPropertyAggregation.Claim {
        val c = p.claim
        NativeSharedPropertyAggregation.compare(listOf(c), asOf)
        require(valid(p.custodyId) && valid(p.registrarId) && valid(p.appraiserId) &&
            p.registrarId != p.appraiserId) { "Missing distinct provenance authorities" }
        require(c.sourceDigest.matches(Regex("[a-f0-9]{64}")) &&
            p.valuationDigest.matches(Regex("[a-f0-9]{64}"))) {
            "Source must contain canonical SHA-256 digests"
        }
        require(p.registrySourceBytes.isNotEmpty() && p.registrySourceBytes.size <= 1_048_576 &&
            p.valuationSourceBytes.isNotEmpty() &&
            p.valuationSourceBytes.size <= 1_048_576) {
            "Missing or oversized original issuer evidence"
        }
        require(digest(p.registrySourceBytes) == c.sourceDigest &&
            digest(p.valuationSourceBytes) == p.valuationDigest) {
            "Original property identity or appraisal source digest mismatch"
        }
        require(ChronoUnit.DAYS.between(c.sourceAsOf, asOf) in 0L..366L &&
            ChronoUnit.DAYS.between(c.valuationAsOf, asOf) in 0L..366L) {
            "Stale property registry or appraisal source"
        }
        val registryKey = roots.registrarEd25519[p.registrarId]
        val appraisalKey = roots.appraiserEd25519[p.appraiserId]
        require(registryKey != null && appraisalKey != null &&
            !MessageDigest.isEqual(registryKey, appraisalKey)) {
            "Independent trusted registry and valuation authorities unavailable"
        }
        verifySignature(registryKey, registryMessage(p), p.registrySignature)
        verifySignature(appraisalKey, appraisalMessage(p), p.valuationSignature)
        return c
    }
}
