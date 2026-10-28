package `in`.growurdhan.model

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

/** Synthetic keys test the protocol only. They are NOT production registry/appraisal trust roots. */
class NativePropertySignedEvidenceTest {
    private val asOf = LocalDate.parse("2026-10-27")
    private fun key(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sign(k: KeyPair, data: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(k.private)
            update(data)
            sign()
        }
    private fun sample(): Pair<NativePropertySignedEvidence.Proof,
        NativePropertySignedEvidence.TrustRoots> {
        val registryKey = key()
        val appraisalKey = key()
        val source = "synthetic registered property record".toByteArray()
        val valuation = "synthetic independently signed valuation".toByteArray()
        val claim = NativeSharedPropertyAggregation.Claim("h-1", "owner-1", "registered-id-1",
            "source-1", sha(source), LocalDate.parse("2026-09-01"), "60",
            100_000L, "valuation-1", LocalDate.parse("2026-10-01"))
        val unsigned = NativePropertySignedEvidence.Proof(claim,"custody-1",
            "test-registry","test-appraiser",source,ByteArray(0),valuation,sha(valuation),ByteArray(0))
        val signed = unsigned.copy(
            registrySignature=sign(registryKey,NativePropertySignedEvidence.registryMessage(unsigned)),
            valuationSignature=sign(appraisalKey,NativePropertySignedEvidence.appraisalMessage(unsigned)))
        return signed to NativePropertySignedEvidence.TrustRoots(
            mapOf("test-registry" to registryKey.public.encoded),
            mapOf("test-appraiser" to appraisalKey.public.encoded))
    }
    @Test fun bothIndependentSyntheticSignaturesValidateAtPinnedDate() {
        val (p,roots)=sample()
        assertEquals("registered-id-1",NativePropertySignedEvidence.verify(p,roots,asOf).registeredPropertyId)
    }
    @Test fun productionNeverPromotesSyntheticIssuer() {
        val (p,_) = sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(p,NativePropertySignedEvidence.productionRoots,asOf)
        }
    }
    @Test fun substitutedOwnershipIsRejectedEvenWithMatchingOriginalSourceDigest() {
        val (p,roots)=sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(p.copy(claim=p.claim.copy(ownerId="foreign")),
                roots,asOf)
        }
    }
    @Test fun amendedValueRequiresIndependentFreshSignature() {
        val (p,roots)=sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(
                p.copy(claim=p.claim.copy(grossPropertyValueMinor=200_000L)),roots,asOf)
        }
    }
    @Test fun mismatchedOriginalRegistryBytesRejected() {
        val (p,roots)=sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(
                p.copy(registrySourceBytes="different".toByteArray()),roots,asOf)
        }
    }
    @Test fun valuationDigestAndOriginalBytesMustAgree() {
        val (p,roots)=sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(
                p.copy(valuationDigest="a".repeat(64)),roots,asOf)
        }
    }
    @Test fun staleRegistryOrAppraisalIsRejected() {
        val (p,roots)=sample()
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(p,roots,LocalDate.parse("2028-01-01"))
        }
    }
    @Test fun sameKeyCannotActAsIndependentAuthority() {
        val (p,roots)=sample()
        val single = roots.registrarEd25519.getValue("test-registry")
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertySignedEvidence.verify(p,
                roots.copy(appraiserEd25519=mapOf("test-appraiser" to single)),asOf)
        }
    }
}
