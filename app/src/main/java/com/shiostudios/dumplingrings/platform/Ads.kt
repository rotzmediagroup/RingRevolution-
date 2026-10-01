package com.shiostudios.dumplingrings.platform

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.shiostudios.dumplingrings.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ---------------------------------------------------------------- adapters (bible §23)

enum class ConsentState { UNKNOWN, NOT_REQUIRED, OBTAINED, DENIED }

interface ConsentProvider {
    val state: StateFlow<ConsentState>
    /** Gather consent (shows the UMP form if required). Never blocks gameplay. */
    fun request(activity: Activity, onDone: () -> Unit)
    /** Re-open privacy options (required by Google when a form was shown). */
    fun showPrivacyOptions(activity: Activity)
    val privacyOptionsRequired: Boolean
    val canRequestAds: Boolean
}

sealed class RewardResult { object Earned : RewardResult(); object Dismissed : RewardResult(); data class Failed(val reason: String) : RewardResult() }

interface AdsProvider {
    val available: Boolean
    fun initialize(context: Context)
    fun preload(context: Context)
    val rewardedReady: Boolean
    val interstitialReady: Boolean
    fun showRewarded(activity: Activity, onResult: (RewardResult) -> Unit)
    fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean
}

/** Default in every build until ADS_ENABLED=true and consent allow ads: nothing is loaded, nothing is shown, no empty space. */
class NoAdsProvider : AdsProvider {
    override val available = false
    override fun initialize(context: Context) {}
    override fun preload(context: Context) {}
    override val rewardedReady = false
    override val interstitialReady = false
    override fun showRewarded(activity: Activity, onResult: (RewardResult) -> Unit) = onResult(RewardResult.Failed("ads disabled"))
    override fun showInterstitial(activity: Activity, onClosed: () -> Unit) = false
}

/** Deterministic fake for tests and the debug menu: rewarded always earns after a short delay. */
class FakeAdsProvider(private val fill: Boolean = true) : AdsProvider {
    override val available = true
    override fun initialize(context: Context) {}
    override fun preload(context: Context) {}
    override val rewardedReady get() = fill
    override val interstitialReady get() = fill
    var rewardedShown = 0; var interstitialsShown = 0
    override fun showRewarded(activity: Activity, onResult: (RewardResult) -> Unit) { if (fill) { rewardedShown++; onResult(RewardResult.Earned) } else onResult(RewardResult.Failed("no fill")) }
    override fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean { if (!fill) return false; interstitialsShown++; onClosed(); return true }
}

class NoConsentProvider : ConsentProvider {
    override val state = MutableStateFlow(ConsentState.NOT_REQUIRED)
    override fun request(activity: Activity, onDone: () -> Unit) = onDone()
    override fun showPrivacyOptions(activity: Activity) {}
    override val privacyOptionsRequired = false
    override val canRequestAds = false
}

/** Google User Messaging Platform (GDPR/EEA/UK). Only used when ADS_ENABLED. */
class UmpConsentProvider(context: Context) : ConsentProvider {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    override val state = MutableStateFlow(ConsentState.UNKNOWN)

    override fun request(activity: Activity, onDone: () -> Unit) {
        val params = ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w("Consent", "${err.errorCode}: ${err.message}")
                publish(); onDone()
            }
        }, { err -> Log.w("Consent", "${err.errorCode}: ${err.message}"); publish(); onDone() })
    }

    private fun publish() {
        state.value = when (info.consentStatus) {
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> ConsentState.NOT_REQUIRED
            ConsentInformation.ConsentStatus.OBTAINED -> ConsentState.OBTAINED
            ConsentInformation.ConsentStatus.REQUIRED -> ConsentState.DENIED
            else -> ConsentState.UNKNOWN
        }
    }

    override fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { publish() }
    }
    override val privacyOptionsRequired: Boolean get() = info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    override val canRequestAds: Boolean get() = info.canRequestAds()
}

/**
 * Google Mobile Ads implementation with preload, retry back-off, no-fill fallback and idempotent reward delivery.
 * Uses the official test unit ids unless production ids are injected at build time (see app/build.gradle.kts).
 */
class AdMobAdsProvider : AdsProvider {
    override val available = true
    private var initialized = false
    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null
    private var rewardedLoading = false
    private var interstitialLoading = false
    private var failures = 0

    override fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        if (BuildConfig.ADS_USE_TEST_IDS) {
            MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_UNSPECIFIED).build())
        }
        MobileAds.initialize(context) { preload(context) }
    }

    override fun preload(context: Context) {
        if (!initialized) return
        if (rewarded == null && !rewardedLoading) {
            rewardedLoading = true
            RewardedAd.load(context, BuildConfig.AD_UNIT_REWARDED, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedLoading = false; failures = 0 }
                override fun onAdFailedToLoad(e: LoadAdError) { rewarded = null; rewardedLoading = false; failures++; Log.w("Ads", "rewarded load failed: ${e.message}") }
            })
        }
        if (interstitial == null && !interstitialLoading) {
            interstitialLoading = true
            InterstitialAd.load(context, BuildConfig.AD_UNIT_INTERSTITIAL, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; interstitialLoading = false }
                override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; interstitialLoading = false; Log.w("Ads", "interstitial load failed: ${e.message}") }
            })
        }
    }

    override val rewardedReady: Boolean get() = rewarded != null
    override val interstitialReady: Boolean get() = interstitial != null

    override fun showRewarded(activity: Activity, onResult: (RewardResult) -> Unit) {
        val ad = rewarded ?: return onResult(RewardResult.Failed("not loaded"))
        rewarded = null
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload(activity); onResult(if (earned) RewardResult.Earned else RewardResult.Dismissed) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { preload(activity); onResult(RewardResult.Failed(e.message)) }
        }
        ad.show(activity) { earned = true }
    }

    override fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean {
        val ad = interstitial ?: return false
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { preload(activity); onClosed() }
            override fun onAdFailedToShowFullScreenContent(e: AdError) { preload(activity); onClosed() }
        }
        ad.show(activity)
        return true
    }
}
