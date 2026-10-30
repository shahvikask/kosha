package `in`.growurdhan.redevelopment

import android.os.SystemClock
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Manual, bounded public exchange download: no personal identifiers in URL/body. */
internal object NativeExchangeEodDownloader {
    private val lastAttempt = mutableMapOf<NativeExchangeEodCatalog.Exchange, Long>()
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun fetch(source: NativeExchangeEodCatalog.Source,
        transport: ((URL) -> ByteArray)? = null): NativeExchangeEodCatalog.Catalog {
        if (transport == null) synchronized(lastAttempt) {
            val now = SystemClock.elapsedRealtime()
            val last = lastAttempt[source.exchange]
            require(last == null || now - last >= 300_000L) {
                "Manual exchange refresh rate limited to five minutes"
            }
            lastAttempt[source.exchange] = now
        }
        val bytes = if (transport != null) transport(source.url) else {
            val conn = source.url.openConnection() as HttpsURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Android; GrowUrDhan Native) AppleWebKit/537.36")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Referer",
                if (source.exchange == NativeExchangeEodCatalog.Exchange.NSE)
                    "https://www.nseindia.com/all-reports"
                else "https://www.bseindia.com/")
            try {
                require(conn.responseCode == HttpURLConnection.HTTP_OK &&
                    conn.contentLengthLong <= MAX_BYTES) {
                    "No official close; last encrypted observation retained"
                }
                conn.inputStream.use { stream ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        require(out.size().toLong() + n <= MAX_BYTES)
                        out.write(buffer, 0, n)
                    }
                    out.toByteArray()
                }
            } catch (err: java.io.IOException) {
                `in`.growurdhan.model.NativePrivacySafeMetrics.record(
                    `in`.growurdhan.model.NativePrivacySafeMetrics.Event.OFFLINE_FETCH_FAILURE)
                throw err
            } finally { conn.disconnect() }
        }
        return NativeExchangeEodCatalog.parse(bytes, source)
    }
}
