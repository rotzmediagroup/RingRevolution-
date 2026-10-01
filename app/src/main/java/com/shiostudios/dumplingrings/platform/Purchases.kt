package com.shiostudios.dumplingrings.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.shiostudios.dumplingrings.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Entitlement snapshot cached offline in the save file; the store is the source of truth when reachable. */
data class EntitlementState(val premium: Boolean, val verified: Boolean, val priceText: String? = null)

sealed class PurchaseOutcome { object Success : PurchaseOutcome(); object Cancelled : PurchaseOutcome(); object Pending : PurchaseOutcome(); data class Failed(val reason: String) : PurchaseOutcome() }

interface PurchaseProvider {
    val available: Boolean
    val state: StateFlow<EntitlementState>
    fun connect(onReady: () -> Unit = {})
    fun buyPremium(activity: Activity, onResult: (PurchaseOutcome) -> Unit)
    fun restore(onResult: (Boolean) -> Unit)
}

/** Store not configured: nothing can be bought; cached entitlement is kept. */
class NoPurchaseProvider(cachedPremium: Boolean) : PurchaseProvider {
    override val available = false
    override val state = MutableStateFlow(EntitlementState(cachedPremium, false))
    override fun connect(onReady: () -> Unit) = onReady()
    override fun buyPremium(activity: Activity, onResult: (PurchaseOutcome) -> Unit) = onResult(PurchaseOutcome.Failed("store unavailable"))
    override fun restore(onResult: (Boolean) -> Unit) = onResult(state.value.premium)
}

class FakePurchaseProvider(cachedPremium: Boolean = false) : PurchaseProvider {
    override val available = true
    override val state = MutableStateFlow(EntitlementState(cachedPremium, true, "€4.99"))
    var nextOutcome: PurchaseOutcome = PurchaseOutcome.Success
    override fun connect(onReady: () -> Unit) = onReady()
    override fun buyPremium(activity: Activity, onResult: (PurchaseOutcome) -> Unit) {
        if (nextOutcome is PurchaseOutcome.Success) state.value = state.value.copy(premium = true)
        onResult(nextOutcome)
    }
    override fun restore(onResult: (Boolean) -> Unit) = onResult(state.value.premium)
}

/**
 * Google Play Billing (one-time, non-consumable "premium"). Verifies purchase state, acknowledges, restores and
 * never revokes the cached entitlement on transient network failure (bible §23).
 */
class PlayBillingProvider(context: Context, cachedPremium: Boolean, private val onEntitlementChanged: (Boolean) -> Unit) : PurchaseProvider {
    override val available = true
    override val state = MutableStateFlow(EntitlementState(cachedPremium, false))
    private var product: ProductDetails? = null
    private var pendingResult: ((PurchaseOutcome) -> Unit)? = null
    private val client: BillingClient = BillingClient.newBuilder(context)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .build()
    private var connected = false

    override fun connect(onReady: () -> Unit) {
        if (connected) return onReady()
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                if (connected) { queryProduct(); restore { }; }
                onReady()
            }
            override fun onBillingServiceDisconnected() { connected = false }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(BuildConfig.PREMIUM_PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build())).build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                product = list.firstOrNull()
                state.value = state.value.copy(priceText = product?.oneTimePurchaseOfferDetails?.formattedPrice)
            }
        }
    }

    override fun buyPremium(activity: Activity, onResult: (PurchaseOutcome) -> Unit) {
        val p = product ?: return onResult(PurchaseOutcome.Failed("product unavailable"))
        pendingResult = onResult
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(p).build())).build()
        val r = client.launchBillingFlow(activity, params)
        if (r.responseCode != BillingClient.BillingResponseCode.OK) { pendingResult = null; onResult(PurchaseOutcome.Failed(r.debugMessage)) }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val cb = pendingResult; pendingResult = null
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val premium = purchases.orEmpty().filter { BuildConfig.PREMIUM_PRODUCT_ID in it.products }
                val purchased = premium.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchased != null) { grant(purchased); cb?.invoke(PurchaseOutcome.Success) }
                else if (premium.any { it.purchaseState == Purchase.PurchaseState.PENDING }) cb?.invoke(PurchaseOutcome.Pending)
                else cb?.invoke(PurchaseOutcome.Failed("no purchase"))
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> cb?.invoke(PurchaseOutcome.Cancelled)
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { restore { }; cb?.invoke(PurchaseOutcome.Success) }
            else -> cb?.invoke(PurchaseOutcome.Failed(result.debugMessage))
        }
    }

    private fun grant(p: Purchase) {
        if (!p.isAcknowledged) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r -> Log.i("Billing", "ack ${r.responseCode}") }
        }
        state.value = state.value.copy(premium = true, verified = true)
        onEntitlementChanged(true)
    }

    override fun restore(onResult: (Boolean) -> Unit) {
        if (!connected) return onResult(state.value.premium)
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync onResult(state.value.premium)
            val owned = purchases.firstOrNull { BuildConfig.PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (owned != null) { grant(owned); onResult(true) }
            else {
                // Verified "not owned" from the store: only now may the cached entitlement be cleared.
                state.value = EntitlementState(false, true, state.value.priceText); onEntitlementChanged(false); onResult(false)
            }
        }
    }
}
