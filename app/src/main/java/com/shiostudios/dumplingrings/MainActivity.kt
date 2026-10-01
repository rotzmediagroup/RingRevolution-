package com.shiostudios.dumplingrings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.ui.screens.*
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.DumplingRingsTheme
import com.shiostudios.dumplingrings.ui.theme.LocalHighContrast
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion
import com.shiostudios.dumplingrings.ui.theme.LocalWorldPalette
import com.shiostudios.dumplingrings.ui.theme.WorldPalette
import androidx.compose.ui.graphics.Color

/** Screens of the game (bible §21). Navigation is a simple back stack; no network, no login wall. */
sealed class Screen {
    object Menu : Screen()
    data class WorldMap(val world: Int) : Screen()
    data class Chapter(val chapter: Int) : Screen()
    data class Play(val level: Int) : Screen()
    object Daily : Screen()
    object Collection : Screen()
    object Shop : Screen()
    object Premium : Screen()
    object Settings : Screen()
    object Privacy : Screen()
    object Credits : Screen()
    object Stats : Screen()
    data class ChapterScene(val chapter: Int, val next: Screen) : Screen()
    data class WorldIntro(val world: Int, val next: Screen) : Screen()
    object Welcome : Screen()
}

class Nav(start: Screen) {
    val stack = mutableStateListOf(start)
    val current: Screen get() = stack.last()
    fun push(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack[stack.size - 1] = s }
    fun pop(): Boolean { if (stack.size <= 1) return false; stack.removeAt(stack.size - 1); return true }
    fun popTo(pred: (Screen) -> Boolean) { while (stack.size > 1 && !pred(stack.last())) stack.removeAt(stack.size - 1) }
    fun reset(s: Screen) { stack.clear(); stack.add(s) }
}

class MainActivity : androidx.appcompat.app.AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val container = DumplingRingsApp.of(this)
        container.ads2.initIfAllowed(this)
        container.purchases.connect { container.purchases.restore { } }
        setContent { AppRoot(container, this) }
    }
}

@Composable
fun AppRoot(container: AppContainer, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val nav = remember { Nav(if (save.firstRunDone) Screen.Menu else Screen.Welcome) }
    val palette = remember(nav.current) {
        val world = when (val s = nav.current) {
            is Screen.WorldMap -> s.world; is Screen.Chapter -> (s.chapter - 1) / 5 + 1; is Screen.Play -> LevelCodec.worldNumber(s.level)
            is Screen.ChapterScene -> (s.chapter - 1) / 5 + 1; is Screen.WorldIntro -> s.world; else -> 1
        }
        val w = container.content.world(world)
        WorldPalette(Color(android.graphics.Color.parseColor(w.palette["primary"])), Color(android.graphics.Color.parseColor(w.palette["accent"])),
            Color(android.graphics.Color.parseColor(w.palette["bg"])), Color(android.graphics.Color.parseColor(w.palette["dark"])))
    }
    LaunchedEffect(save.settings) { container.audio.settings = save.settings; container.haptics.enabled = save.settings.haptics }
    // music per screen
    LaunchedEffect(nav.current) {
        val s = nav.current
        val (music, amb) = when (s) {
            is Screen.Play -> { val w = container.content.worldOf(s.level); val lv = container.content.level(s.level); (if (lv.isChefLevel) w.finaleMusic else w.music) to w.ambience }
            is Screen.WorldMap -> container.content.world(s.world).let { it.music to it.ambience }
            is Screen.Chapter -> container.content.world((s.chapter - 1) / 5 + 1).let { it.music to it.ambience }
            is Screen.Daily -> container.content.world(1).let { it.music to it.ambience }
            else -> "menu_theme" to null
        }
        container.audio.playMusic(music); container.audio.playAmbience(amb)
    }
    var backRequested by remember { mutableStateOf(0) }
    BackHandler(enabled = nav.stack.size > 1 || nav.current is Screen.Play) { backRequested++ }

    DumplingRingsTheme {
        CompositionLocalProvider(LocalWorldPalette provides palette, LocalReduceMotion provides save.settings.reduceMotion, LocalHighContrast provides save.settings.highContrast) {
            Box(Modifier.fillMaxSize()) {
                when (val s = nav.current) {
                    Screen.Welcome -> WelcomeScreen(container) { container.save.update { it.copy(firstRunDone = true) }; nav.reset(Screen.Menu) }
                    Screen.Menu -> MainMenuScreen(container, nav)
                    is Screen.WorldMap -> WorldMapScreen(container, nav, s.world)
                    is Screen.Chapter -> ChapterScreen(container, nav, s.chapter)
                    is Screen.Play -> GameplayScreen(container, nav, activity, s.level, backRequested)
                    Screen.Daily -> DailyScreen(container, nav, activity)
                    Screen.Collection -> CollectionScreen(container, nav)
                    Screen.Shop -> ShopScreen(container, nav, activity)
                    Screen.Premium -> PremiumScreen(container, nav, activity)
                    Screen.Settings -> SettingsScreen(container, nav, activity)
                    Screen.Privacy -> PrivacyScreen(container, nav, activity)
                    Screen.Credits -> CreditsScreen(container, nav)
                    Screen.Stats -> StatsScreen(container, nav)
                    is Screen.ChapterScene -> ChapterSceneScreen(container, s.chapter) { nav.replace(s.next) }
                    is Screen.WorldIntro -> WorldIntroScreen(container, s.world) { nav.replace(s.next) }
                }
            }
        }
    }
    LaunchedEffect(backRequested) {
        if (backRequested == 0) return@LaunchedEffect
        if (nav.current !is Screen.Play) { container.audio.sfx("button_back", 0.7f); nav.pop() }
    }
}
