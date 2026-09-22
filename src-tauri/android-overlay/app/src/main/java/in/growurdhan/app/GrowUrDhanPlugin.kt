package `in`.growurdhan.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.activity.result.ActivityResult
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
import java.io.File
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

    override fun load(webView: android.webkit.WebView) {
        val pending = PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        billing = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(pending)
            .build()
        connectBilling()
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
            invoke.reject(e.message ?: "Could not read the encrypted vault.")
        }
    }

    @Command
    fun vault_blob_save(invoke: Invoke) {
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
            invoke.reject(e.message ?: "Could not write the encrypted vault.")
        }
    }

    @Command
    fun play_licence_status(invoke: Invoke) {
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
            result.put("lifetime", owned.any { it.products.contains("gud_lifetime") })
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
        if (testApk()) {
            invoke.resolve(JSObject())
            return
        }
        val args = invoke.parseArgs(BuyArgs::class.java)
        val client = billing ?: return invoke.reject("Google Play Billing is not ready.")
        if (!installedFromPlay()) {
            return invoke.reject("Install GrowUrDhan from Google Play to buy a licence.")
        }
        if (args.sku == "gud_support_annual") {
            queryKind(client, BillingClient.ProductType.INAPP) { inapp ->
                val hasLifetime = inapp.any {
                    it.purchaseState == Purchase.PurchaseState.PURCHASED && it.products.contains("gud_lifetime")
                }
                if (!hasLifetime) {
                    invoke.reject("Buy the ₹249 lifetime licence first. Support cannot be purchased on its own.")
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
        play_licence_status(invoke)
    }

    @Command
    fun play_review(invoke: Invoke) {
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
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )
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
            invoke.reject(e.message ?: "Could not write the scheduled backup.")
        }
    }

    @Command
    fun backup_save(invoke: Invoke) {
        val args = invoke.parseArgs(WriteBackupArgs::class.java)
        pendingSaveBytes = Base64.decode(args.bytesB64, Base64.DEFAULT)
        pendingSaveName = args.filename.ifBlank { "growurdhan.kosha" }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, pendingSaveName)
        }
        startActivityForResult(invoke, intent, "backupSaveResult")
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
            invoke.reject(e.message ?: "Could not write the backup file.")
        }
    }

    @Command
    fun backup_open(invoke: Invoke) {
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
            invoke.reject(e.message ?: "Could not open that backup.")
        }
    }

    @Command
    fun app_exit(invoke: Invoke) {
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
}
