package `in`.growurdhan.app

import android.app.Activity
import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.tauri.annotation.ActivityCallback
import app.tauri.annotation.Command
import app.tauri.annotation.InvokeArg
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.security.SecureRandom

/**
 * Device-bound vault + Play Billing.
 * On GUD-Android-APK, BuildConfig.GUD_TEST_APK lets a sideloaded debug
 * install start a trial. Play AAB builds leave that flag unset / false.
 */
@TauriPlugin
class GrowUrDhanPlugin(private val activity: Activity) : Plugin(activity), PurchasesUpdatedListener {
    private val vaultFile get() = File(activity.filesDir, "vault.enc")
    private val handler = Handler(Looper.getMainLooper())
    private var billing: BillingClient? = null
    private var pendingPurchase: Invoke? = null
    private var pendingSaveBytes: ByteArray? = null
    private var pendingSaveName: String? = null
    private val gmail = GmailClient(activity)
    private val gmailScope = "https://www.googleapis.com/auth/gmail.readonly"

    override fun load(webView: android.webkit.WebView) {
        val pending = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        billing = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(pending)
            .build()
        connectBilling()
        Diag.banner(activity)
        try {
            ensurePython()
            val started = try { Python.isStarted() } catch (e: Exception) { Diag.log(activity, "python", "isStarted threw " + Diag.throwable(e)); false }
            Diag.log(activity, "python", "load ensurePython done started=$started")
        } catch (e: Exception) {
            Diag.log(activity, "python", "load ensurePython FAILED " + Diag.throwable(e))
        }
    }

    // Chaquopy docs: Python.start must run on an Application/Activity/Service
    // thread — not a worker thread — exactly once per process.
    private fun ensurePython() {
        val thread = Thread.currentThread().name + " main=" + (Looper.myLooper() == Looper.getMainLooper())
        val already = try { Python.isStarted() } catch (e: Exception) {
            Diag.log(activity, "python", "isStarted before start threw on $thread " + Diag.throwable(e))
            false
        }
        Diag.log(activity, "python", "ensurePython thread=$thread alreadyStarted=$already")
        if (already) return
        val startNow = {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(activity.applicationContext))
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            synchronized(GrowUrDhanPlugin::class.java) { startNow() }
            return
        }
        val latch = CountDownLatch(1)
        var err: Exception? = null
        handler.post {
            try {
                synchronized(GrowUrDhanPlugin::class.java) { startNow() }
            } catch (e: Exception) {
                err = e
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        err?.let { throw it }
    }

    private fun pythonError(e: Throwable): String {
        val text = Diag.throwable(e)
        return if (text.isBlank()) "Engine is not running (empty ${e.javaClass.name})." else text
    }

    private fun connectBilling() {
        billing?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {}
            override fun onBillingServiceDisconnected() {
                handler.postDelayed({ connectBilling() }, 1500)
            }
        })
    }

    private fun master(): MasterKey =
        MasterKey.Builder(activity)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private fun licencePrefs() = EncryptedSharedPreferences.create(
        activity,
        "gud_licence",
        master(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun testApk(): Boolean {
        return try {
            val klass = Class.forName(activity.packageName + ".BuildConfig")
            klass.getField("GUD_TEST_APK").getBoolean(null)
        } catch (_: Exception) {
            false
        }
    }

    private fun installedFromPlay(): Boolean {
        return try {
            val info = activity.packageManager.getInstallSourceInfo(activity.packageName)
            val installer = info.installingPackageName ?: info.initiatingPackageName
            installer == "com.android.vending" || installer == "com.google.android.feedback"
        } catch (_: Exception) {
            false
        }
    }

    private fun trustedInstall(): Boolean = testApk() || installedFromPlay()

    private fun ensureTrialStarted(trusted: Boolean): String? {
        if (!trusted) return null
        val prefs = licencePrefs()
        val existing = prefs.getString("trialStartedAt", null)
        if (existing != null) return existing
        val now = java.time.Instant.now().toString()
        prefs.edit().putString("trialStartedAt", now).apply()
        return now
    }

    @Command
    fun vault_blob_load(invoke: Invoke) {
        Diag.enter(activity, "vault_blob_load")
        try {
            if (!vaultFile.exists()) {
                invoke.resolve(null)
                return
            }
            val encrypted = EncryptedFile.Builder(
                activity,
                vaultFile,
                master(),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            val bytes = encrypted.openFileInput().use { it.readBytes() }
            val out = JSObject()
            out.put("value", Base64.encodeToString(bytes, Base64.NO_WRAP))
            invoke.resolve(out)
        } catch (e: Exception) {
            Diag.log(activity, Diag.current, "fail " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not read the encrypted vault.")
        }
    }

    @Command
    fun vault_blob_save(invoke: Invoke) {
        Diag.enter(activity, "vault_blob_save")
        try {
            val args = invoke.parseArgs(SaveArgs::class.java)
            val bytes = Base64.decode(args.bytesB64, Base64.DEFAULT)
            if (vaultFile.exists()) vaultFile.delete()
            val encrypted = EncryptedFile.Builder(
                activity,
                vaultFile,
                master(),
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encrypted.openFileOutput().use { it.write(bytes) }
            invoke.resolve(JSObject())
        } catch (e: Exception) {
            Diag.log(activity, Diag.current, "fail " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not write the encrypted vault.")
        }
    }

    @Command
    fun play_licence_status(invoke: Invoke) {
        Diag.enter(activity, "play_licence_status")
        resolveLicence(invoke, 0)
    }

    private fun resolveLicence(invoke: Invoke, attempt: Int) {
        val trusted = trustedInstall()
        val client = billing
        if (client == null || !client.isReady) {
            if (installedFromPlay() && attempt < 8) {
                handler.postDelayed({ resolveLicence(invoke, attempt + 1) }, 350)
                return
            }
            val fallback = JSObject()
            fallback.put("lifetime", false)
            fallback.put("supportActive", false)
            fallback.put("integrity", trusted)
            fallback.put("trialStartedAt", ensureTrialStarted(trusted))
            invoke.resolve(fallback)
            return
        }
        val result = JSObject()
        result.put("integrity", trusted)
        queryKind(client, BillingClient.ProductType.INAPP) { inapp ->
            val owned = inapp.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            acknowledgeAll(client, owned)
            val ids = owned.flatMap { it.products }.toSet()
            val manual = ids.contains("gud_lifetime")
            val gmailDirect = ids.contains("gud_gmail")
            val gmailUpgrade = ids.contains("gud_gmail_upgrade")
            result.put("lifetime", manual || gmailDirect || gmailUpgrade)
            result.put("gmail", gmailDirect || gmailUpgrade)
            queryKind(client, BillingClient.ProductType.SUBS) { subs ->
                val live = subs.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                acknowledgeAll(client, live)
                val support = live.firstOrNull { it.products.contains("gud_support_annual") }
                result.put("supportActive", support != null)
                result.put("supportRenewsAt", null)
                result.put("playAccountBound", owned.isNotEmpty() || support != null)
                result.put("trialStartedAt", ensureTrialStarted(trusted))
                invoke.resolve(result)
            }
        }
        pingIntegrity()
    }

    @Command
    fun play_purchase(invoke: Invoke) {
        Diag.enter(activity, "play_purchase")
        if (testApk()) {
            invoke.resolve(JSObject())
            return
        }
        val args = invoke.parseArgs(BuyArgs::class.java)
        val client = billing ?: return invoke.reject("Google Play Billing is not ready.")
        if (!installedFromPlay()) {
            return invoke.reject("Install GrowUrDhan from Google Play to buy a licence.")
        }
        if (args.sku == "gud_support_annual" || args.sku == "gud_lifetime" || args.sku == "gud_gmail" || args.sku == "gud_gmail_upgrade") {
            queryKind(client, BillingClient.ProductType.INAPP) { inapp ->
                val ids = inapp.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { it.products }.toSet()
                val manual = ids.contains("gud_lifetime")
                val gmailDirect = ids.contains("gud_gmail")
                val gmailUpgrade = ids.contains("gud_gmail_upgrade")
                val perpetual = manual || gmailDirect || gmailUpgrade
                val gmailOn = gmailDirect || gmailUpgrade
                if (args.sku == "gud_support_annual" && !perpetual) {
                    invoke.reject("Buy a perpetual licence first. The annual service cannot be purchased on its own.")
                    return@queryKind
                }
                if (args.sku == "gud_lifetime" && perpetual) {
                    invoke.reject("A perpetual licence is already on this Play account.")
                    return@queryKind
                }
                if (args.sku == "gud_gmail" && (manual || gmailOn)) {
                    invoke.reject(if (gmailOn) "Gmail sync is already on this Play account." else "The ₹499 licence is closed. Use the ₹250 upgrade.")
                    return@queryKind
                }
                if (args.sku == "gud_gmail_upgrade" && (!manual || gmailOn)) {
                    invoke.reject(if (!manual) "Buy the ₹249 licence before the ₹250 upgrade." else "Gmail sync is already on this Play account.")
                    return@queryKind
                }
                launchBuy(invoke, client, args)
            }
            return
        }
        launchBuy(invoke, client, args)
    }

    private fun launchBuy(invoke: Invoke, client: BillingClient, args: BuyArgs) {
        pendingPurchase = invoke
        val productType = if (args.sku == "gud_support_annual") BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(args.sku)
            .setProductType(productType)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { _, details ->
            val item: ProductDetails = productDetailsListOf(details).firstOrNull()
                ?: return@queryProductDetailsAsync invoke.reject("Product ${args.sku} is not in Play Console yet.")
            val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(item)
            val offers = item.subscriptionOfferDetails
            if (offers != null && offers.isNotEmpty()) {
                val wanted = args.offer
                val offer = offers.firstOrNull { od ->
                    wanted != null && (od.offerId == wanted || od.basePlanId == wanted)
                } ?: offers.firstOrNull { od -> od.offerId.isNullOrEmpty() && od.basePlanId == "standalone" }
                    ?: offers.firstOrNull { od -> od.basePlanId == "standalone" }
                    ?: offers.first()
                paramsBuilder.setOfferToken(offer.offerToken)
            }
            val flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(paramsBuilder.build()))
                .setObfuscatedAccountId("gud")
                .build()
            client.launchBillingFlow(activity, flow)
        }
    }

    @Command
    fun play_restore(invoke: Invoke) {
        Diag.enter(activity, "play_restore")
        play_licence_status(invoke)
    }

    @Command
    fun play_review(invoke: Invoke) {
        Diag.enter(activity, "play_review")
        if (testApk() || !installedFromPlay()) {
            openPlayListing()
            invoke.resolve(reviewResult(false, true))
            return
        }
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    openPlayListing()
                    invoke.resolve(reviewResult(false, true))
                    return@addOnCompleteListener
                }
                manager.launchReviewFlow(activity, task.result).addOnCompleteListener {
                    invoke.resolve(reviewResult(true, false))
                }
            }
        } catch (_: Exception) {
            openPlayListing()
            invoke.resolve(reviewResult(false, true))
        }
    }

    @Command
    fun play_open_listing(invoke: Invoke) {
        Diag.enter(activity, "play_open_listing")
        openPlayListing()
        invoke.resolve(JSObject())
    }

    private fun reviewResult(launched: Boolean, fallback: Boolean): JSObject {
        val out = JSObject()
        out.put("launched", launched)
        out.put("fallback", fallback)
        return out
    }

    private fun openPlayListing() {
        val id = activity.packageName
        try {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id"))
            market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(market)
        } catch (_: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
            web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(web)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val invoke = pendingPurchase
        pendingPurchase = null
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            billing?.let { client -> acknowledgeAll(client, purchases.orEmpty()) }
            invoke?.resolve(JSObject())
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            invoke?.reject("Purchase cancelled.")
        } else {
            invoke?.reject(result.debugMessage ?: "Play Billing error ${result.responseCode}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun productDetailsListOf(details: Any?): List<ProductDetails> {
        if (details == null) return emptyList()
        if (details is List<*>) return details.filterIsInstance<ProductDetails>()
        return try {
            val method = details.javaClass.methods.firstOrNull { it.name == "getProductDetailsList" }
            val list = method?.invoke(details)
            if (list is List<*>) list.filterIsInstance<ProductDetails>() else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun acknowledgeAll(client: BillingClient, purchases: List<Purchase>) {
        for (p in purchases) {
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.purchaseToken)
                    .build()
                client.acknowledgePurchase(params) { }
            }
        }
    }

    private fun queryKind(client: BillingClient, type: String, done: (List<Purchase>) -> Unit) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(type).build()) { _, list ->
            done(list)
        }
    }

    private fun pingIntegrity() {
        try {
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
            IntegrityManagerFactory.create(activity).requestIntegrityToken(
                IntegrityTokenRequest.builder()
                    .setNonce(Base64.encodeToString(nonce, Base64.NO_WRAP))
                    .build()
            )
        } catch (_: Exception) {
        }
    }

    private fun backupPrefs() = EncryptedSharedPreferences.create(
        activity,
        "gud_backup",
        master(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun backupTreeUri(): Uri? {
        val raw = backupPrefs().getString("treeUri", null) ?: return null
        return try {
            Uri.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    @Command
    fun backup_pick_folder(invoke: Invoke) {
        Diag.enter(activity, "backup_pick_folder")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        startActivityForResult(invoke, intent, "backupPickFolderResult")
    }

    @ActivityCallback
    fun backupPickFolderResult(invoke: Invoke, result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) {
            val cancelled = JSObject()
            cancelled.put("chosen", false)
            invoke.resolve(cancelled)
            return
        }
        val uri = result.data?.data
        if (uri == null) {
            val cancelled = JSObject()
            cancelled.put("chosen", false)
            invoke.resolve(cancelled)
            return
        }
        try {
            val flags = (result.data?.flags ?: 0) and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            activity.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: Exception) {
        }
        backupPrefs().edit().putString("treeUri", uri.toString()).apply()
        val tree = DocumentFile.fromTreeUri(activity, uri)
        val out = JSObject()
        out.put("chosen", true)
        out.put("label", tree?.name ?: "Cloud folder")
        invoke.resolve(out)
    }

    @Command
    fun backup_clear_folder(invoke: Invoke) {
        Diag.enter(activity, "backup_clear_folder")
        val uri = backupTreeUri()
        if (uri != null) {
            try {
                activity.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
        }
        backupPrefs().edit().remove("treeUri").apply()
        invoke.resolve(JSObject())
    }

    @Command
    fun backup_write_scheduled(invoke: Invoke) {
        Diag.enter(activity, "backup_write_scheduled")
        try {
            val args = invoke.parseArgs(WriteBackupArgs::class.java)
            val uri = backupTreeUri() ?: return invoke.reject("Choose a Google Drive or OneDrive folder first.")
            val tree = DocumentFile.fromTreeUri(activity, uri)
                ?: return invoke.reject("That folder is no longer available. Pick it again.")
            if (!tree.canWrite()) return invoke.reject("GrowUrDhan cannot write in that folder. Pick it again.")
            val bytes = Base64.decode(args.bytesB64, Base64.DEFAULT)
            val created = tree.createFile("application/octet-stream", args.filename)
                ?: return invoke.reject("Could not create the backup file in that folder.")
            activity.contentResolver.openOutputStream(created.uri, "w").use { os ->
                if (os == null) return invoke.reject("Could not write the backup file.")
                os.write(bytes)
            }
            val keep = if (args.keepLast > 0) args.keepLast else 5
            val kosha = tree.listFiles()
                .filter { it.isFile && (it.name ?: "").endsWith(".kosha") }
                .sortedByDescending { it.lastModified() }
            kosha.drop(keep).forEach { it.delete() }
            val remaining = tree.listFiles().count { it.isFile && (it.name ?: "").endsWith(".kosha") }
            val out = JSObject()
            out.put("kept", remaining)
            out.put("label", tree.name ?: "Cloud folder")
            invoke.resolve(out)
        } catch (e: Exception) {
            Diag.log(activity, Diag.current, "fail " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not write the scheduled backup.")
        }
    }

    @Command
    fun backup_save(invoke: Invoke) {
        Diag.enter(activity, "backup_save")
        val args = invoke.parseArgs(WriteBackupArgs::class.java)
        pendingSaveBytes = Base64.decode(args.bytesB64, Base64.DEFAULT)
        pendingSaveName = args.filename.ifBlank { "growurdhan.kosha" }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, pendingSaveName)
        }
        try {
            startActivityForResult(invoke, intent, "backupSaveResult")
        } catch (e: Exception) {
            pendingSaveBytes = null
            pendingSaveName = null
            Diag.log(activity, "backup_save", "dialog failed " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not open the save dialog on this phone.")
        }
    }

    @ActivityCallback
    fun backupSaveResult(invoke: Invoke, result: ActivityResult) {
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        pendingSaveName = null
        if (result.resultCode != Activity.RESULT_OK || result.data?.data == null || bytes == null) {
            val out = JSObject()
            out.put("saved", false)
            invoke.resolve(out)
            return
        }
        try {
            activity.contentResolver.openOutputStream(result.data!!.data!!, "w").use { os ->
                if (os == null) {
                    invoke.reject("Could not write the backup file.")
                    return
                }
                os.write(bytes)
            }
            val out = JSObject()
            out.put("saved", true)
            invoke.resolve(out)
        } catch (e: Exception) {
            Diag.log(activity, Diag.current, "fail " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not write the backup file.")
        }
    }

    @Command
    fun backup_open(invoke: Invoke) {
        Diag.enter(activity, "backup_open")
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(invoke, intent, "backupOpenResult")
    }

    @ActivityCallback
    fun backupOpenResult(invoke: Invoke, result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data?.data == null) {
            invoke.resolve(null)
            return
        }
        try {
            val bytes = activity.contentResolver.openInputStream(result.data!!.data!!)?.use { it.readBytes() }
            if (bytes == null) {
                invoke.resolve(null)
                return
            }
            val out = JSObject()
            out.put("value", Base64.encodeToString(bytes, Base64.NO_WRAP))
            invoke.resolve(out)
        } catch (e: Exception) {
            Diag.log(activity, Diag.current, "fail " + Diag.throwable(e))
            invoke.reject(e.message ?: "Could not open that backup.")
        }
    }

    @Command
    fun cas_health(invoke: Invoke) {
        Diag.enter(activity, "cas_health")
        Thread {
            try {
                ensurePython()
                val py = Python.getInstance()
                Diag.log(activity, "cas_health", "Python.getInstance ok started=${Python.isStarted()}")
                val raw = py.getModule("cas_android").callAttr("health").toString()
                val parsed = try { JSONObject(raw) } catch (je: Exception) {
                    Diag.log(activity, "cas_health", "health JSON parse failed " + Diag.throwable(je) + " raw=" + raw.take(500))
                    JSONObject()
                }
                val ready = parsed.optBoolean("ok", false)
                val err = parsed.optString("error", "")
                val trace = parsed.optString("trace", "")
                Diag.log(activity, "cas_health", "health ok=$ready error=$err trace=$trace detail=${raw.take(2500)}")
                val out = JSObject()
                out.put("ok", ready)
                out.put("engine", "PYTHON_CASPARSER")
                out.put("detail", raw)
                out.put("logPath", Diag.publicPath)
                out.put("logPrivate", Diag.privatePath)
                out.put("logError", Diag.lastError)
                if (!ready) {
                    val shown = if (err.isNotBlank()) err else if (trace.isNotBlank()) trace else "cas_health ok=false with no error text"
                    out.put("error", shown)
                }
                invoke.resolve(out)
            } catch (e: Exception) {
                val msg = pythonError(e)
                Diag.log(activity, "cas_health", "FAILED $msg")
                val out = JSObject()
                out.put("ok", false)
                out.put("engine", "PYTHON_CASPARSER")
                out.put("error", msg)
                out.put("logPath", Diag.publicPath)
                out.put("logPrivate", Diag.privatePath)
                out.put("logError", Diag.lastError)
                invoke.resolve(out)
            }
        }.start()
    }

    @Command
    fun cas_parse(invoke: Invoke) {
        Diag.enter(activity, "cas_parse")
        val args = try {
            invoke.parseArgs(CasParseArgs::class.java)
        } catch (e: Exception) {
            Diag.log(activity, "cas_parse", "args failed " + Diag.throwable(e))
            invoke.reject("Missing statement file.")
            return
        }
        val pdfLen = args.pdfBase64?.length ?: 0
        val passLen = args.password?.length ?: 0
        Diag.log(activity, "cas_parse", "file=${args.filename} pdfChars=$pdfLen passwordChars=$passLen")
        Thread {
            try {
                ensurePython()
                val json = Python.getInstance().getModule("cas_android")
                    .callAttr("parse_b64", args.pdfBase64 ?: "", args.password ?: "", args.filename ?: "statement.pdf")
                    .toString()
                val parsed = try { JSONObject(json) } catch (_: Exception) { JSONObject() }
                val holdings = parsed.optJSONArray("holdings")
                Diag.log(
                    activity,
                    "cas_parse",
                    "result ok=${parsed.optBoolean("ok")} code=${parsed.optString("code")} fileType=${parsed.optString("fileType")} error=${parsed.optString("error")} holdings=${holdings?.length() ?: -1} detail=${parsed.optString("detail").take(1200)}"
                )
                val out = JSObject()
                out.put("payload", json)
                invoke.resolve(out)
            } catch (e: Exception) {
                val msg = pythonError(e)
                Diag.log(activity, "cas_parse", "FAILED $msg")
                invoke.reject(msg)
            }
        }.start()
    }

    @Command
    fun prices_refresh(invoke: Invoke) {
        Diag.enter(activity, "prices_refresh")
        Thread {
            try {
                val args = try {
                    invoke.parseArgs(PricesArgs::class.java)
                } catch (_: Exception) {
                    PricesArgs()
                }
                val (catalog, asOf, warnings) = PriceFetcher.refresh(args.isins)
                val out = JSObject()
                val matched = catalog.length() > 0
                out.put("ok", matched || asOf != null)
                out.put("asOf", asOf)
                out.put("catalog", catalog)
                out.put("catalogCount", catalog.length())
                out.put("warnings", warnings)
                if (!matched && args.isins.isNotBlank()) {
                    val bits = ArrayList<String>()
                    for (i in 0 until warnings.length()) {
                        val w = warnings.optString(i)
                        if (w.isNotBlank()) bits.add(w)
                    }
                    val extra = if (bits.isEmpty()) "" else " " + bits.joinToString(" ")
                    out.put("ok", false)
                    out.put("error", "No last close or NAV matched the ISINs in this vault.$extra")
                }
                invoke.resolve(out)
            } catch (e: Exception) {
                Diag.log(activity, "prices_refresh", "FAILED " + Diag.throwable(e))
                invoke.reject(e.message ?: "Could not refresh prices on this phone.")
            }
        }.start()
    }

    @Command
    fun diag_log(invoke: Invoke) {
        val line = try {
            invoke.parseArgs(DiagLineArgs::class.java).line
        } catch (e: Exception) {
            Diag.log(activity, "diag_log", "args failed " + Diag.throwable(e))
            ""
        }
        if (line.isNotBlank()) Diag.log(activity, "caller", line)
        val out = JSObject()
        out.put("ok", Diag.lastError.isBlank())
        out.put("path", Diag.publicPath)
        out.put("privatePath", Diag.privatePath)
        out.put("writeError", Diag.lastError)
        invoke.resolve(out)
    }

    @Command
    fun diag_status(invoke: Invoke) {
        val out = JSObject()
        out.put("ok", true)
        out.put("enabled", Diag.isEnabled(activity))
        invoke.resolve(out)
    }

    @Command
    fun diag_set(invoke: Invoke) {
        val on = try {
            invoke.parseArgs(DiagToggleArgs::class.java).enabled
        } catch (_: Exception) {
            false
        }
        Diag.setEnabled(activity, on)
        if (on) Diag.log(activity, "diag", "diagnostics enabled")
        val out = JSObject()
        out.put("ok", true)
        out.put("enabled", Diag.isEnabled(activity))
        invoke.resolve(out)
    }

    @Command
    fun app_exit(invoke: Invoke) {
        Diag.enter(activity, "app_exit")
        invoke.resolve(JSObject())
        activity.finishAffinity()
    }

    @InvokeArg
    class SaveArgs {
        @JvmField
        var bytesB64: String = ""
    }

    @InvokeArg
    class BuyArgs {
        @JvmField
        var sku: String = ""
        @JvmField
        var offer: String? = null
    }

    @InvokeArg
    class WriteBackupArgs {
        @JvmField
        var filename: String = ""
        @JvmField
        var bytesB64: String = ""
        @JvmField
        var keepLast: Int = 5
    }

    @InvokeArg
    class PricesArgs {
        @JvmField
        var isins: String = ""
    }

    @InvokeArg
    class CasParseArgs {
        @JvmField
        var pdfBase64: String = ""
        @JvmField
        var password: String = ""
        @JvmField
        var filename: String = "statement.pdf"
    }

    @InvokeArg
    class DiagLineArgs {
        @JvmField
        var line: String = ""
    }

    @InvokeArg
    class DiagToggleArgs {
        @JvmField
        var enabled: Boolean = false
    }

    @InvokeArg
    class GmailConnectArgs {
        @JvmField
        var chooseAccount: Boolean = false
    }

    @Command
    fun gmail_connect(invoke: Invoke) {
        Diag.enter(activity, "gmail_connect")
        val choose = try {
            invoke.parseArgs(GmailConnectArgs::class.java).chooseAccount
        } catch (_: Exception) {
            false
        }
        // A second inbox must show the account list. The first grant must not be reused silently.
        val forcePicker = choose || gmail.emails().isNotEmpty()
        handler.post {
            if (forcePicker) pickGoogleAccount(invoke) else authorizeGmail(invoke, null)
        }
    }

    @Command
    fun gmail_disconnect(invoke: Invoke) {
        val email = invoke.parseArgs(GmailEmailArgs::class.java).email
        gmail.forget(email)
        val out = JSObject()
        out.put("ok", true)
        invoke.resolve(out)
    }

    @Command
    fun gmail_status(invoke: Invoke) {
        invoke.resolve(JSObject.fromJSONObject(gmail.status()))
    }

    @Command
    fun gmail_search(invoke: Invoke) {
        val args = invoke.parseArgs(GmailEmailArgs::class.java)
        Thread {
            try {
                val token = tokenFor(args.email)
                val listed = if (args.pageToken.isBlank() && gmail.historyOf(args.email).isNotBlank())
                    gmail.listSince(token, args.email)
                else
                    gmail.listPdfs(token, args.email, args.pageToken)
                invoke.resolve(JSObject.fromJSONObject(listed))
            } catch (e: Exception) {
                invoke.reject(e.message ?: "Could not look through Gmail.")
            }
        }.start()
    }

    @Command
    fun gmail_fetch(invoke: Invoke) {
        val args = invoke.parseArgs(GmailFetchArgs::class.java)
        Thread {
            try {
                val token = tokenFor(args.email)
                val out = JSObject()
                out.put("ok", true)
                out.put("bytesB64", gmail.fetchPdf(token, args.messageId, args.attachmentId))
                invoke.resolve(out)
            } catch (e: Exception) {
                invoke.reject(e.message ?: "Could not download that statement.")
            }
        }.start()
    }

    @Command
    fun gmail_advance(invoke: Invoke) {
        val args = invoke.parseArgs(GmailEmailArgs::class.java)
        if (args.done) {
            gmail.advance(args.email)
            gmail.saveHistory(args.email, args.historyId)
        } else gmail.savePage(args.email, args.pageToken)
        val out = JSObject()
        out.put("ok", true)
        invoke.resolve(out)
    }

    @ActivityCallback
    fun gmailAuthResult(invoke: Invoke, result: ActivityResult) {
        val code = result.resultCode
        Diag.log(activity, "gmail_connect", "auth result code=" + code + " hasData=" + (result.data != null))
        if (result.data != null) {
            try {
                val auth = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data)
                finishGmailAuth(invoke, auth.accessToken)
                return
            } catch (e: Exception) {
                Diag.log(activity, "gmail_connect", "auth result err " + Diag.throwable(e))
                invoke.reject(e.message ?: "Google sign-in failed.")
                return
            }
        }
        if (code != Activity.RESULT_OK) {
            invoke.reject("Google closed sign-in (code " + code + "). Choose shahvikask@gmail.com and allow read access.")
            return
        }
        invoke.reject("Google sign-in returned no account.")
    }

    private fun pickGoogleAccount(invoke: Invoke) {
        val intent = AccountManager.newChooseAccountIntent(
            null,
            null,
            arrayOf("com.google"),
            true,
            "Choose the Gmail inbox that receives these statements",
            null,
            null,
            null
        )
        startActivityForResult(invoke, intent, "gmailAccountPickResult")
    }

    @ActivityCallback
    fun gmailAccountPickResult(invoke: Invoke, result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            invoke.reject("Google sign-in was cancelled.")
            return
        }
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME).orEmpty()
        if (email.isBlank()) {
            invoke.reject("Choose a Gmail account.")
            return
        }
        Diag.log(activity, "gmail_connect", "picked $email")
        authorizeGmail(invoke, email)
    }

    private fun authorizeGmail(invoke: Invoke, email: String?) {
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(gmailScope)))
        if (!email.isNullOrBlank()) builder.setAccount(Account(email, "com.google"))
        Identity.getAuthorizationClient(activity).authorize(builder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending == null) {
                        invoke.reject("Google sign-in could not open.")
                        return@addOnSuccessListener
                    }
                    startIntentSenderForResult(invoke, IntentSenderRequest.Builder(pending.intentSender).build(), "gmailAuthResult")
                } else {
                    finishGmailAuth(invoke, result.accessToken)
                }
            }
            .addOnFailureListener { e ->
                invoke.reject(e.message ?: "Google sign-in failed.")
            }
    }

    private fun finishGmailAuth(invoke: Invoke, token: String?) {
        if (token.isNullOrBlank()) {
            invoke.reject("Google did not allow Gmail to be read.")
            return
        }
        Thread {
            try {
                val email = gmail.profileEmail(token)
                if (email.isBlank()) {
                    invoke.reject("Gmail did not return an email address.")
                    return@Thread
                }
                gmail.remember(email)
                val out = JSObject()
                out.put("ok", true)
                out.put("email", email)
                invoke.resolve(out)
            } catch (e: Exception) {
                invoke.reject(e.message ?: "Could not read this Gmail account.")
            }
        }.start()
    }

    private fun tokenFor(email: String): String {
        if (email.isBlank()) throw IllegalStateException("Choose a Gmail account first.")
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Gmail refresh must not run on the screen thread.")
        }
        val latch = java.util.concurrent.CountDownLatch(1)
        var token: String? = null
        var err: String? = null
        handler.post { authorizeForToken(email, { token = it; latch.countDown() }, { err = it; latch.countDown() }) }
        if (!latch.await(20, java.util.concurrent.TimeUnit.SECONDS)) {
            throw IllegalStateException("Gmail sign-in timed out. Connect again.")
        }
        return token ?: throw IllegalStateException(err ?: "Gmail needs you to connect again.")
    }

    private fun authorizeForToken(email: String, ok: (String) -> Unit, bad: (String) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(gmailScope)))
            .setAccount(Account(email, "com.google"))
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                val token = result.accessToken
                if (result.hasResolution() || token.isNullOrBlank()) {
                    bad("Gmail needs you to connect again.")
                } else {
                    ok(token)
                }
            }
            .addOnFailureListener { e -> bad(e.message ?: "Could not use this Gmail.") }
    }

    @InvokeArg
    class GmailEmailArgs {
        @JvmField
        var email: String = ""
        @JvmField
        var pageToken: String = ""
        @JvmField
        var done: Boolean = false
        @JvmField
        var historyId: String = ""
    }

    @InvokeArg
    class GmailFetchArgs {
        @JvmField
        var email: String = ""
        @JvmField
        var messageId: String = ""
        @JvmField
        var attachmentId: String = ""
    }
}
