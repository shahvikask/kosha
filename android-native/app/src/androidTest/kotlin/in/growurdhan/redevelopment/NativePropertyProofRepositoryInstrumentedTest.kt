package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.model.NativeCredentials
import `in`.growurdhan.model.NativeDeviceAdmission
import `in`.growurdhan.model.NativePropertySignedEvidence
import `in`.growurdhan.model.NativeRegistrationProof
import `in`.growurdhan.model.NativeSharedPropertyAggregation
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** API35 SQLCipher proof using synthetic test-only issuers; NOT authenticated legal title. */
@RunWith(AndroidJUnit4::class)
class NativePropertyProofRepositoryInstrumentedTest {
    private val registry = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val appraiser = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private val roots = NativePropertySignedEvidence.TrustRoots(
        mapOf("test-registry" to registry.public.encoded),
        mapOf("test-appraiser" to appraiser.public.encoded))
    private val date = LocalDate.parse("2026-10-27")
    private val input = NativeCredentials.Enrollment("owner@example.org", "Owner", "Family",
        "Strong!Pass123", "school", "First School", "pet", "First Pet")
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir,"property-signed-"+UUID.randomUUID()).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root,"databases/$name").also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File = File(root,"no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File = File(root,"files").also { it.mkdirs() }
        }
    }
    private fun gate() = NativeDeviceAdmission().also {
        it.onStart()
        check(it.begin() && it.complete(true))
    }
    private fun create(ctx: Context): Pair<NativeAccountRepository,NativeBooksRepository> {
        val accounts = NativeAccountRepository(ctx,gate())
        accounts.enrollVerified(input,NativeRegistrationProof.Identity("ABCDE1234F","1990-06-12"),
            NativeRegistrationProof.Consent(NativeRegistrationProof.TERMS_VERSION,
                NativeRegistrationProof.TERMS_TEXT_ID,"2026-10-19T10:00:00Z",true))
        assertTrue(accounts.login(input.email,input.password))
        return accounts to NativeBooksRepository(accounts)
    }
    private fun sign(key: KeyPair, payload: ByteArray): ByteArray =
        Signature.getInstance("Ed25519").run {
            initSign(key.private)
            update(payload)
            sign()
        }
    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun proof(holding: String, owner: String, custody: String,
        percent: String, value: Long = 100_000L): NativePropertySignedEvidence.Proof {
        val original = "synthetic authenticated test registry row".toByteArray()
        val valuation = "synthetic independent signed valuation row".toByteArray()
        val claim = NativeSharedPropertyAggregation.Claim(holding,owner,"synthetic-property-id",
            "test-source",digest(original),LocalDate.parse("2026-09-01"),
            percent,value,"test-independent-appraisal",LocalDate.parse("2026-10-01"))
        val unsigned = NativePropertySignedEvidence.Proof(claim,custody,
            "test-registry","test-appraiser",original,ByteArray(0),
            valuation,digest(valuation),ByteArray(0))
        return unsigned.copy(
            registrySignature=sign(registry,NativePropertySignedEvidence.registryMessage(unsigned)),
            valuationSignature=sign(appraiser,NativePropertySignedEvidence.appraisalMessage(unsigned)))
    }
    private fun holding(b: NativeBooksRepository, owner: String): Pair<String,String> {
        val custody=b.createCustody(input.email,
            NativeBooksRepository.NewCustody(owner,"OTHER","Synthetic registrar"))
        val h=b.createHolding(input.email,NativeBooksRepository.NewHolding(owner,custody,
            "Synthetic direct property","REAL_ESTATE","Residential",0L,0L,"2026-01-01",false))
        return h to custody
    }
    @Test fun twoSyntheticSignedOwnersAreNotDoubleCountedAndSurviveEncryptedRestart() {
        val ctx=sandbox();val (accounts,books)=create(ctx)
        val owner=books.read(input.email).people.single().id
        val joint=books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Synthetic Joint","Joint"))
        val (a,ac)=holding(books,owner);val (b,bc)=holding(books,joint)
        val original=books.read(input.email).holdings
        val repo=NativePropertyProofRepository(accounts,roots)
        assertNull(repo.preview(input.email,date))
        repo.register(input.email,proof(a,owner,ac,"60"),date)
        repo.register(input.email,proof(b,joint,bc,"40"),date)
        val sum=repo.preview(input.email,date)!!
        assertEquals(1,sum.properties.size)
        assertEquals(100_000L,sum.householdGrossMinor)
        assertEquals(100_000L,sum.attributedMinor)
        assertEquals(0L,sum.unallocatedMinor)
        assertEquals(2,sum.properties.single().shares.size)
        assertEquals(original,books.read(input.email).holdings)
        assertTrue(books.history(input.email,a).isEmpty())
        assertTrue(books.history(input.email,b).isEmpty())
        accounts.onBackground()
        val reopened=NativeAccountRepository(ctx,gate())
        assertTrue(reopened.login(input.email,input.password))
        assertEquals(sum,NativePropertyProofRepository(reopened,roots).preview(input.email,date))
        // Real shipping app has no trusted issuer roots; even existing synthetic rows
        // must not become visible as verified household values.
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertyProofRepository(reopened).preview(input.email,date)
        }
    }
    @Test fun signedButForeignHoldingOwnerRejectedBeforeAnyProofWrites() {
        val ctx=sandbox();val (accounts,books)=create(ctx)
        val owner=books.read(input.email).people.single().id
        val joint=books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Synthetic Joint","Joint"))
        val (h,c)=holding(books,owner)
        val repo=NativePropertyProofRepository(accounts,roots)
        val before=books.read(input.email).holdings
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(input.email,proof(h,joint,c,"100"),date)
        }
        assertNull(repo.preview(input.email,date))
        assertEquals(before,books.read(input.email).holdings)
    }
    @Test fun duplicateRegistrationConflictingAppraisalAndDirectSqlEditsRollback() {
        val ctx=sandbox();val (accounts,books)=create(ctx)
        val owner=books.read(input.email).people.single().id
        val joint=books.createPerson(input.email,
            NativeBooksRepository.NewPerson("Synthetic Joint","Joint"))
        val (a,ac)=holding(books,owner);val (b,bc)=holding(books,joint)
        val repo=NativePropertyProofRepository(accounts,roots)
        val first=proof(a,owner,ac,"60")
        repo.register(input.email,first,date)
        assertThrows(Exception::class.java) { repo.register(input.email,first,date) }
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(input.email,proof(b,joint,bc,"40",200_000L),date)
        }
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT count(*) FROM native_p11_property_proof",null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1L,it.getLong(0))
            }
            assertThrows(Exception::class.java) {
                db.execSQL("UPDATE native_p11_property_proof SET gross_value_minor=200000")
            }
            assertThrows(Exception::class.java) {
                db.execSQL("DELETE FROM native_p11_property_proof")
            }
        }
        val sum=repo.preview(input.email,date)!!
        assertEquals(100_000L,sum.householdGrossMinor)
        assertEquals(60_000L,sum.attributedMinor)
        assertEquals(40_000L,sum.unallocatedMinor)
    }
    @Test fun mismatchedDigestStaleProofAndUntrustedSignerDoNotCreateTable() {
        val (accounts,books)=create(sandbox())
        val owner=books.read(input.email).people.single().id
        val (h,c)=holding(books,owner)
        val p=proof(h,owner,c,"100")
        val repo=NativePropertyProofRepository(accounts,roots)
        val before=books.read(input.email).holdings
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(input.email,p.copy(registrySourceBytes="mismatch".toByteArray()),date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            repo.register(input.email,p,LocalDate.parse("2028-01-01"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativePropertyProofRepository(accounts).register(input.email,p,date)
        }
        assertNull(repo.preview(input.email,date))
        accounts.withUnlockedVault(input.email) { db ->
            db.rawQuery("SELECT count(*) FROM sqlite_master WHERE name='native_p11_property_proof'",
                null).use {
                assertTrue(it.moveToFirst())
                assertEquals(0L,it.getLong(0))
            }
        }
        assertEquals(before,books.read(input.email).holdings)
        assertTrue(books.history(input.email,h).isEmpty())
    }
}
