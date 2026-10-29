package `in`.growurdhan.redevelopment

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.QueryProductDetailsParams
import `in`.growurdhan.model.NativePlaySkuCatalog

/**
 * Actual Google Play Billing SDK catalog discovery, deliberately READ-ONLY.
 * No purchase flow, raw token storage, local receipt trust or acknowledgement.
 * A product being found in Play does not grant a licence or trial.
 */
internal class NativePlayCatalogProbe(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { _, _ -> /* purchase callbacks never grant entitlements */ }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder()
            .enableOneTimeProducts().build())
        .build()
    private var closed = false
    private var delivered = false
    private var started = false

    data class Status(val report: NativePlaySkuCatalog.Report?,
        val unavailable: Boolean)

    fun close() {
        if (closed) return
        closed = true
        client.endConnection()
    }

    fun readCatalog(onDone: (Status) -> Unit) {
        check(!started && !closed) { "Billing probe is one-shot" }
        started = true
        fun finish(report: NativePlaySkuCatalog.Report?) {
            if (delivered || closed) return
            delivered = true
            main.post {
                if (!closed) onDone(Status(report, report == null))
                close()
            }
        }
        val groups = NativePlaySkuCatalog.expected.groupBy { it.type }.entries.toList()
        val found = mutableListOf<NativePlaySkuCatalog.Item>()
        fun queryGroup(index: Int) {
            if (closed || delivered) return
            if (index >= groups.size) {
                finish(NativePlaySkuCatalog.inspect(found))
                return
            }
            val (kind, items) = groups[index]
            val playType = if (kind == NativePlaySkuCatalog.Type.INAPP)
                BillingClient.ProductType.INAPP else BillingClient.ProductType.SUBS
            val products = items.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it.id)
                    .setProductType(playType)
                    .build()
            }
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(products).build()
            client.queryProductDetailsAsync(params) { result, details ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    finish(null)
                } else {
                    found += details.productDetailsList.map {
                        NativePlaySkuCatalog.Item(it.productId, kind)
                    }
                    queryGroup(index + 1)
                }
            }
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) queryGroup(0)
                else finish(null)
            }
            override fun onBillingServiceDisconnected() { finish(null) }
        })
    }
}
