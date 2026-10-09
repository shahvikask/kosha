package `in`.growurdhan.app.cas

import android.content.Context
import android.net.Uri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import `in`.growurdhan.core.model.OwnerId
import `in`.growurdhan.domain.finance.CasImportContract
import `in`.growurdhan.domain.finance.CasParsedHolding
import `in`.growurdhan.domain.finance.CasParsedReport
import `in`.growurdhan.domain.finance.CasValidatedPreview
import `in`.growurdhan.domain.finance.CasValidationException
import `in`.growurdhan.domain.finance.FinancialHolding
import java.io.InputStream
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Frozen CASPARSER is invoked on-phone only; NO PDF upload or developer-sidecar
 * endpoint. When the native Python/PDFium runtime isn't packaged and verified,
 * refuse the import rather than inventing a parser result.
 */
interface CasPythonEngine {
    fun parsePdfBase64(pdfBase64: String, password: String): String
}

class CasParserUnavailable : IllegalStateException(
    "The on-device CASPARSER/PDFium runtime is not installed or verified in this build."
)

/**
 * Adapter to the existing Chaquopy Python API through reflection, keeping the
 * normal Kotlin/UI builds independent of an unverified native PDFium wheel.
 * The Python module gud_cas_bridge.py is a non-frozen bootstrapper; the parser
 * source itself remains the unchanged vendored CASPARSER.
 */
@Singleton
class ChaquopyCasEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : CasPythonEngine {
    override fun parsePdfBase64(pdfBase64: String, password: String): String {
        try {
            val pythonClass = Class.forName("com.chaquo.python.Python")
            val started = pythonClass.getMethod("isStarted").invoke(null) as Boolean
            if (!started) {
                val androidPlatform = Class.forName("com.chaquo.python.android.AndroidPlatform")
                val platform = androidPlatform.getConstructor(Context::class.java).newInstance(context)
                val start = pythonClass.methods.single {
                    it.name == "start" && it.parameterTypes.size == 1
                }
                start.invoke(null, platform)
            }
            val runtime = pythonClass.getMethod("getInstance").invoke(null)
            val module = runtime.javaClass.getMethod("getModule", String::class.java)
                .invoke(runtime, "gud_cas_bridge")
            val call = module.javaClass.methods.first {
                it.name == "callAttr" && it.parameterTypes.size == 2
            }
            return call.invoke(
                module, "parse_b64", arrayOf(pdfBase64, password, "statement.pdf"),
            ).toString()
        } catch (_: Exception) {
            // Never surface Python traceback, CAS contents, password or PAN.
            throw CasParserUnavailable()
        } catch (_: LinkageError) {
            throw CasParserUnavailable()
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CasRuntimeBindings {
    @Binds abstract fun engine(impl: ChaquopyCasEngine): CasPythonEngine
}

/** A PDF cap protects against large files and broad in-memory amplification. */
object CasPdfBytes {
    fun read(stream: InputStream): ByteArray {
        var backing = ByteArray(32 * 1024)
        var size = 0
        var stalled = 0
        try {
            while (true) {
                if (size == backing.size) {
                    if (size == CasImportContract.MAX_PDF_BYTES) {
                        // Detect oversize before growing, without leaving excess PDF bytes.
                        if (stream.read() != -1) {
                            throw CasValidationException("PDF exceeds the 30 MiB on-device import limit.")
                        }
                        break
                    }
                    val replacement = ByteArray(minOf(
                        CasImportContract.MAX_PDF_BYTES,
                        Math.multiplyExact(backing.size, 2),
                    ))
                    backing.copyInto(replacement, endIndex = size)
                    backing.fill(0)
                    backing = replacement
                }
                val n = stream.read(backing, size, backing.size - size)
                if (n == -1) break
                if (n == 0) {
                    stalled++
                    if (stalled > 16) {
                        throw CasValidationException("The selected PDF could not be read.")
                    }
                    continue
                }
                stalled = 0
                size += n
            }
            if (size < 8 ||
                !backing.copyOfRange(0, 5).contentEquals(byteArrayOf(37, 80, 68, 70, 45))
            ) throw CasValidationException("The selected file is not a PDF document.")
            return backing.copyOf(size)
        } finally {
            backing.fill(0) // no non-wiped duplicate ByteArrayOutputStream buffer
        }
    }
}

@Singleton
class NativeCasPdfReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: CasPythonEngine,
) {
    suspend fun parseAndValidate(
        uri: Uri,
        owner: OwnerId,
        holdings: List<FinancialHolding>,
        pdfPassword: String = "",
    ): CasValidatedPreview = withContext(Dispatchers.IO) {
        val pdf = context.contentResolver.openInputStream(uri)?.use(CasPdfBytes::read)
            ?: throw CasValidationException("Unable to open the selected PDF.")
        try {
            val sha = MessageDigest.getInstance("SHA-256").digest(pdf)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            val result = parser.parsePdfBase64(Base64.getEncoder().encodeToString(pdf), pdfPassword)
            if (result.length > 4 * 1024 * 1024)
                throw CasValidationException("CAS parser returned excessive output.")
            val json = try { JSONObject(result) } catch (_: Exception) {
                throw CasValidationException("CAS parser did not return valid structured results.")
            }
            if (!json.optBoolean("ok", false)) {
                throw CasValidationException("The PDF is unsupported, damaged or password protected.")
            }
            val rows = json.optJSONArray("holdings")
                ?: throw CasValidationException("CAS parser did not return holdings.")
            if (rows.length() > CasImportContract.MAX_PARSED_HOLDINGS)
                throw CasValidationException("CAS parser returned too many holdings.")
            val parsed = ArrayList<CasParsedHolding>(rows.length())
            for (i in 0 until rows.length()) {
                val line = rows.optJSONObject(i)
                    ?: throw CasValidationException("Unexpected CAS holding structure.")
                parsed += CasParsedHolding(
                    name = line.optString("name"),
                    category = line.optString("category"),
                    quantityDecimal = line.optString("quantity"),
                    unitPriceDecimal = line.optString("price"),
                    investedDecimal = if (line.has("cost") && !line.isNull("cost"))
                        line.optString("cost") else null,
                    isin = line.optString("isin").takeIf { it.isNotBlank() },
                    folio = line.optString("folio").takeIf { it.isNotBlank() },
                )
            }
            val warnings = json.optJSONArray("warnings")
            val report = CasParsedReport(
                engine = json.optString("engine"),
                fileType = json.optString("fileType"),
                complete = json.optBoolean("complete", false),
                statementDate = json.optString("periodTo"),
                warnings = if (warnings == null) emptyList()
                    else List(warnings.length()) { "CAS parser warning" },
                sourceSha256 = sha,
                holdings = parsed,
            )
            CasImportContract.validate(report, owner, holdings, LocalDate.now())
        } finally {
            pdf.fill(0)
        }
    }
}
