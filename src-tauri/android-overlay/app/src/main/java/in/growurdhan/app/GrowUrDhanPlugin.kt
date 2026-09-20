package `in`.growurdhan.app

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
import java.io.File
import java.security.SecureRandom

/**
 * Device-bound vault + Play Billing.
 * The AES wrapping key never leaves Android Keystore.
 * Purchases are bound to the Google Play account, not this APK file.
 * A sideloaded / extracted APK fails the installer check and stays locked.
 */
@TauriPlugin
class GrowUrDhanPlugin(private val activity: Activity) : Plugin(activity), PurchasesUpdatedListener {
    private val vaultFile get() = File(activity.filesDir, "vault.enc")
    private val handler = Handler(Looper.getMainLooper())
    private var billing: BillingClient? = null
    private var pendingPurchase: Invoke? = null

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

    private fun installedFromPlay(): Boolean {
        return try {
            val info = activity.packageManager.getInstallSourceInfo(activity.packageName)
            val installer = info.installingPackageName ?: info.initiatingPackageName
            installer == "com.android.vending" || installer == "com.google.android.feedback"
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureTrialStarted(fromPlay: Boolean): String? {
        if (!fromPlay) return null
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
        val fromPlay = installedFromPlay()
        val client = billing
        if (client == null || !client.isReady) {
            if (fromPlay && attempt < 8) {
                handler.postDelayed({ resolveLicence(invoke, attempt + 1) }, 350)
                return
            }
            val fallback = JSObject()
            fallback.put("lifetime", false)
            fallback.put("supportActive", false)
            fallback.put("integrity", fromPlay)
            fallback.put("trialStartedAt", ensureTrialStarted(fromPlay))
            invoke.resolve(fallback)
            return
        }
        val result = JSObject()
        result.put("integrity", fromPlay)
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
                result.put("trialStartedAt", ensureTrialStarted(fromPlay))
                invoke.resolve(result)
            }
        }
        pingIntegrity()
    }

    @Command
    fun play_purchase(invoke: Invoke) {
        val args = invoke.parseArgs(BuyArgs::class.java)
        val client = billing ?: return invoke.reject("Google Play Billing is not ready.")
        if (!installedFromPlay()) {
            return invoke.reject("Install GrowUrDhan from Google Play to buy a licence.")
        }
        pendingPurchase = invoke
        val productType = if (args.sku == "gud_support_annual") BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(args.sku)
            .setProductType(productType)
            .build()
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        ) { _, details ->
            val item: ProductDetails = details.productDetailsList?.firstOrNull()
                ?: return@queryProductDetailsAsync invoke.reject("Product ${args.sku} is not in Play Console yet.")
            val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(item)
            val offers = item.subscriptionOfferDetails
            if (offers != null && offers.isNotEmpty()) {
                val wanted = args.offer
                val offer = offers.firstOrNull { od ->
                    wanted != null && (od.offerId == wanted || od.basePlanId == wanted)
                } ?: offers.first()
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
            /* Installer check is the hard gate; Integrity is extra signal. */
        }
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
}
