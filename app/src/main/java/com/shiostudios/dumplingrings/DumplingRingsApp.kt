package com.shiostudios.dumplingrings

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.shiostudios.dumplingrings.assets.AssetCatalog
import com.shiostudios.dumplingrings.audio.GameAudio
import com.shiostudios.dumplingrings.audio.Haptics
import com.shiostudios.dumplingrings.content.ContentRepository
import com.shiostudios.dumplingrings.core.systems.AdsConfig
import com.shiostudios.dumplingrings.core.systems.AdsPolicy
import com.shiostudios.dumplingrings.core.systems.BoosterLedger
import com.shiostudios.dumplingrings.platform.AdMobAdsProvider
import com.shiostudios.dumplingrings.platform.AdsProvider
import com.shiostudios.dumplingrings.platform.ConsentProvider
import com.shiostudios.dumplingrings.platform.ConsentState
import com.shiostudios.dumplingrings.platform.NoAdsProvider
import com.shiostudios.dumplingrings.platform.NoConsentProvider
import com.shiostudios.dumplingrings.platform.NoPurchaseProvider
import com.shiostudios.dumplingrings.platform.PlayBillingProvider
import com.shiostudios.dumplingrings.platform.PurchaseProvider
import com.shiostudios.dumplingrings.platform.RewardResult
import com.shiostudios.dumplingrings.platform.UmpConsentProvider
import com.shiostudios.dumplingrings.save.SaveStore
import java.util.UUID

/** Dependency container. Everything is created lazily and injectable for tests. */
class AppContainer(val context: Context) {
    val lowMemory: Boolean = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).let { it.isLowRamDevice || it.memoryClass <= 128 }
    val content = ContentRepository(context)
    val assets = AssetCatalog(context, lowMemory)
    val save = SaveStore(context)
    val audio = GameAudio(context).also { it.settings = save.current.settings }
    val haptics = Haptics(context).also { it.enabled = save.current.settings.haptics }

    val adsConfig = AdsConfig(enabled = BuildConfig.ADS_ENABLED, bannerEnabled = false, interstitialEnabled = BuildConfig.ADS_ENABLED, rewardedEnabled = BuildConfig.ADS_ENABLED)
    val consent: ConsentProvider = if (BuildConfig.ADS_ENABLED) UmpConsentProvider(context) else NoConsentProvider()
    val ads: AdsProvider = if (BuildConfig.ADS_ENABLED) AdMobAdsProvider() else NoAdsProvider()
    val purchases: PurchaseProvider = if (BuildConfig.IAP_ENABLED) PlayBillingProvider(context, save.current.premium) { premium ->
        save.update { it.copy(premium = premium, premiumVerifiedAt = System.currentTimeMillis()) }
    } else NoPurchaseProvider(save.current.premium)

    val ads2 = AdsManager(this)
}

/** Placement, consent and reward orchestration (idempotent reward grants, no-fill fallback, caps). */
class AdsManager(private val c: AppContainer) {
    private var sessionStart = System.currentTimeMillis()
    val consentGiven: Boolean get() = c.consent.state.value == ConsentState.OBTAINED || c.consent.state.value == ConsentState.NOT_REQUIRED
    val rewardedAvailable: Boolean get() = AdsPolicy.rewardedAllowed(c.adsConfig, consentGiven) && c.ads.rewardedReady
    val rewardedOffered: Boolean get() = AdsPolicy.rewardedAllowed(c.adsConfig, consentGiven) && c.ads.available

    fun initIfAllowed(activity: Activity) {
        if (!c.adsConfig.enabled) return
        c.consent.request(activity) {
            if (c.consent.canRequestAds) { c.ads.initialize(activity.applicationContext); c.ads.preload(activity.applicationContext) }
        }
    }

    /** Rewarded flow with an idempotent transaction id: the grant is committed exactly once. */
    fun showRewarded(activity: Activity, grant: (requestId: String) -> Unit, onResult: (RewardResult) -> Unit) {
        if (!rewardedAvailable) return onResult(RewardResult.Failed("unavailable"))
        val requestId = "rw-" + UUID.randomUUID()
        c.ads.showRewarded(activity) { r ->
            if (r is RewardResult.Earned) grant(requestId)
            onResult(r)
        }
    }

    /** Interstitial between screens only; returns true if one was shown (caller waits for onClosed). */
    fun maybeInterstitial(activity: Activity, levelIndex: Int, isChef: Boolean, justPurchased: Boolean, onClosed: () -> Unit): Boolean {
        val save = c.save.current
        val firstSession = save.sessionCount <= 1
        val ok = AdsPolicy.interstitialAllowed(c.adsConfig, save, levelIndex, isChef, System.currentTimeMillis(), consentGiven, justPurchased, firstSession)
        if (!ok || !c.ads.interstitialReady) return false
        val shown = c.ads.showInterstitial(activity) {
            c.save.update { it.copy(completedLevelsSinceInterstitial = 0, lastInterstitialAt = System.currentTimeMillis(), interstitialsThisSession = it.interstitialsThisSession + 1) }
            onClosed()
        }
        return shown
    }
}

class DumplingRingsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.save.update { it.copy(sessionCount = it.sessionCount + 1, interstitialsThisSession = 0) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { container.audio.onResume() }
            override fun onStop(owner: LifecycleOwner) { container.audio.onPause() }
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) container.assets.trimToHalf()
        if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) container.assets.trim()
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig) }

    companion object {
        fun of(context: Context): AppContainer = (context.applicationContext as DumplingRingsApp).container
    }
}
