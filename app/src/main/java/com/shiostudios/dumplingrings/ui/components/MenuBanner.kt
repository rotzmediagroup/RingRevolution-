package com.shiostudios.dumplingrings.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.BuildConfig
import com.shiostudios.dumplingrings.core.systems.AdsPolicy

/** Menu-only adaptive banner. No space is reserved until an ad actually loads. */
@Composable
fun MenuBanner(container: AppContainer) {
    val save by container.save.state.collectAsState()
    val consent = container.consent.state.collectAsState().value
    val allowed = remember(save.premium, consent) {
        AdsPolicy.bannerAllowed(container.adsConfig, save, container.consent.canRequestAds, true)
    }
    if (!allowed || BuildConfig.AD_UNIT_BANNER.isBlank()) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(width) { AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width) }
        var loaded by remember(width) { mutableStateOf(false) }
        val view = remember(width) {
            AdView(context).apply {
                adUnitId = BuildConfig.AD_UNIT_BANNER
                setAdSize(adSize)
                adListener = object : AdListener() {
                    override fun onAdLoaded() { loaded = true }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loaded = false
                        Log.w("Ads", "menu banner: ${error.code} ${error.message}")
                    }
                }
            }
        }
        LaunchedEffect(view) { view.loadAd(AdRequest.Builder().build()) }
        DisposableEffect(view, lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.resume()
                    Lifecycle.Event.ON_PAUSE -> view.pause()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer); view.adListener = object : AdListener() {}; view.destroy() }
        }
        Box(Modifier.fillMaxWidth().clipToBounds().height(if (loaded) adSize.height.dp else 0.dp)) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(adSize.height.dp))
        }
    }
}
