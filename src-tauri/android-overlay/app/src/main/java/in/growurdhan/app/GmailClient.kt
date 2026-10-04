package `in`.growurdhan.app

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone

/**
 * Gmail read of statement PDFs only. The access token stays in this
 * process. JavaScript receives file names and, when asked, one PDF.
 */
class GmailClient(private val context: Context) {
    private fun prefs() = EncryptedSharedPreferences.create(
        context,
        "gud_gmail",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun emails(): List<String> {
        val raw = prefs().getString("emails", "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun remember(email: String) {
        val next = (emails() + email.trim()).distinct()
        prefs().edit().putString("emails", next.joinToString("\n")).apply()
    }

    fun forget(email: String) {
        val next = emails().filter { !it.equals(email, ignoreCase = true) }
        prefs().edit().putString("emails", next.joinToString("\n")).remove("after.$email").apply()
    }

    fun afterOf(email: String): String = prefs().getString("after.$email", "") ?: ""

    fun pageOf(email: String): String = prefs().getString("page.$email", "") ?: ""

    fun historyOf(email: String): String = prefs().getString("history.$email", "") ?: ""

    fun saveHistory(email: String, historyId: String) {
        if (historyId.isBlank()) return
        prefs().edit().putString("history.$email", historyId).remove("page.$email").apply()
    }

    fun savePage(email: String, pageToken: String) {
        val edit = prefs().edit()
        if (pageToken.isBlank()) edit.remove("page.$email") else edit.putString("page.$email", pageToken)
        edit.apply()
    }

    fun advance(email: String) {
        prefs().edit().putString("after.$email", yesterday()).remove("page.$email").apply()
    }

    fun status(): JSONObject {
        val accounts = JSONArray()
        for (email in emails()) {
            val after = afterOf(email)
            val row = JSONObject()
            row.put("email", email)
            row.put("after", after)
            row.put("first", after.isEmpty())
            row.put("page", pageOf(email))
            row.put("historyId", historyOf(email))
            accounts.put(row)
        }
        val out = JSONObject()
        out.put("ok", true)
        out.put("accounts", accounts)
        return out
    }

    fun profileEmail(token: String): String {
        val body = httpGet("https://gmail.googleapis.com/gmail/v1/users/me/profile", token)
        return JSONObject(body).optString("emailAddress")
    }

    fun listPdfs(token: String, email: String, pageToken: String): JSONObject {
        val after = afterOf(email).ifBlank { "2026/01/01" }
        val q = "$STATEMENT_QUERY after:$after"
        val url = StringBuilder("https://gmail.googleapis.com/gmail/v1/users/me/messages?maxResults=8&q=")
        url.append(URLEncoder.encode(q, "UTF-8"))
        if (pageToken.isNotBlank()) {
            url.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
        }
        val listed = JSONObject(httpGet(url.toString(), token))
        val files = JSONArray()
        val messages = listed.optJSONArray("messages")
        if (messages != null) {
            for (i in 0 until messages.length()) {
                val id = messages.getJSONObject(i).optString("id")
                if (id.isBlank()) continue
                if (i > 0) Thread.sleep(120)
                collectPdfs(token, id, files)
            }
        }
        val out = JSONObject()
        out.put("ok", true)
        out.put("files", files)
        out.put("pageToken", listed.optString("nextPageToken"))
        out.put("historyId", listed.optString("historyId"))
        return out
    }

    /** Mail added since the last successful replace. A 404 means the cursor expired. */
    fun listSince(token: String, email: String): JSONObject {
        val start = historyOf(email)
        if (start.isBlank()) return listPdfs(token, email, "")
        val url = "https://gmail.googleapis.com/gmail/v1/users/me/history?maxResults=20&historyTypes=messageAdded&startHistoryId=" +
            URLEncoder.encode(start, "UTF-8")
        val listed = try {
            JSONObject(httpGet(url, token))
        } catch (e: IllegalStateException) {
            if (e.message?.contains("(404)") == true) {
                prefs().edit().remove("history.$email").apply()
                return listPdfs(token, email, "")
            }
            throw e
        }
        val files = JSONArray()
        val history = listed.optJSONArray("history")
        if (history != null) {
            for (i in 0 until history.length()) {
                val added = history.getJSONObject(i).optJSONArray("messagesAdded") ?: continue
                for (j in 0 until added.length()) {
                    val id = added.getJSONObject(j).optJSONObject("message")?.optString("id").orEmpty()
                    if (id.isBlank()) continue
                    if (files.length() > 0) Thread.sleep(120)
                    collectPdfs(token, id, files)
                }
            }
        }
        val out = JSONObject()
        out.put("ok", true)
        out.put("files", files)
        out.put("pageToken", "")
        out.put("historyId", listed.optString("historyId").ifBlank { start })
        out.put("incremental", true)
        return out
    }

    fun fetchPdf(token: String, messageId: String, attachmentId: String): String {
        val url = "https://gmail.googleapis.com/gmail/v1/users/me/messages/" +
            URLEncoder.encode(messageId, "UTF-8") + "/attachments/" +
            URLEncoder.encode(attachmentId, "UTF-8")
        val body = JSONObject(httpGet(url, token))
        val size = body.optInt("size", 0)
        if (size > MAX_PDF_BYTES) throw IllegalStateException("That statement is too large to import.")
        val data = body.optString("data")
        if (data.isBlank()) throw IllegalStateException("Gmail did not return the PDF.")
        val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP)
        if (bytes.size > MAX_PDF_BYTES) throw IllegalStateException("That statement is too large to import.")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun collectPdfs(token: String, messageId: String, out: JSONArray) {
        val message = JSONObject(
            httpGet(
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId?format=full",
                token
            )
        )
        val payload = message.optJSONObject("payload") ?: return
        val from = header(payload, "From")
        val date = header(payload, "Date")
        walk(payload, messageId, from, date, out)
    }

    private fun walk(part: JSONObject, messageId: String, from: String, date: String, out: JSONArray) {
        val filename = part.optString("filename")
        val attachmentId = part.optJSONObject("body")?.optString("attachmentId").orEmpty()
        if (filename.endsWith(".pdf", ignoreCase = true) && attachmentId.isNotEmpty()) {
            val row = JSONObject()
            row.put("messageId", messageId)
            row.put("attachmentId", attachmentId)
            row.put("filename", filename)
            row.put("from", from)
            row.put("date", date)
            out.put(row)
        }
        val parts = part.optJSONArray("parts") ?: return
        for (i in 0 until parts.length()) {
            walk(parts.getJSONObject(i), messageId, from, date, out)
        }
    }

    private fun header(payload: JSONObject, name: String): String {
        val headers = payload.optJSONArray("headers") ?: return ""
        for (i in 0 until headers.length()) {
            val h = headers.getJSONObject(i)
            if (h.optString("name").equals(name, ignoreCase = true)) return h.optString("value")
        }
        return ""
    }

    private fun httpGet(url: String, token: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 15000
        conn.readTimeout = 40000
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 403 || code == 429) {
            val reason = try { org.json.JSONObject(text).optJSONObject("error")?.optString("message").orEmpty() } catch (_: Exception) { "" }
            throw IllegalStateException(if (reason.isBlank()) "Gmail refused the request ($code)." else "Gmail refused the request ($code): $reason")
        }
        if (code !in 200..299) throw IllegalStateException("Gmail refused the request ($code).")
        return text
    }

    private fun yesterday(): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DATE, -1)
        return String.format(
            "%04d/%02d/%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    companion object {
        private const val MAX_PDF_BYTES = 12_000_000
        private const val STATEMENT_QUERY =
            "has:attachment filename:pdf (" +
                "from:ecas@cdslstatement.com OR " +
                "from:nsdl-cas@nsdl.co.in OR from:nsdlcas.nsdl.com OR " +
                "from:camsonline.com OR " +
                "from:kfintech.com OR " +
                "from:nps-statements@mailer.proteantech.in OR from:mailer.proteantech.in)"
    }
}
