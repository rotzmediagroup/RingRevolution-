package com.shiostudios.dumplingrings.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.DumplingRingsApp
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.Screen
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.ui.screens.*
import com.shiostudios.dumplingrings.ui.theme.DumplingRingsTheme
import kotlinx.coroutines.MainScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/**
 * JVM screenshot tests (Robolectric native graphics). They render the real composables with the real assets and write
 * PNGs to build/screenshots for visual QA; they also fail if a screen throws during composition/draw.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h914dp-xxhdpi", application = DumplingRingsApp::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun container(): AppContainer = DumplingRingsApp.of(rule.activity)

    /** Frame loops and infinite transitions never go idle, so the test clock is driven manually. */
    private fun shoot(name: String) {
        // let async asset decodes (IO dispatcher) land, then drive a few frames on the test clock
        repeat(8) { rule.mainClock.advanceTimeByFrame(); rule.waitForIdle(); Thread.sleep(60) }
        rule.mainClock.advanceTimeBy(900)
        rule.waitForIdle()
        val decor = rule.activity.window.decorView
        val w = decor.width.coerceAtLeast(1); val h = decor.height.coerceAtLeast(1)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        decor.draw(android.graphics.Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
    }

    private val content = androidx.compose.runtime.mutableStateOf<@androidx.compose.runtime.Composable () -> Unit>({})
    private fun host() {
        rule.mainClock.autoAdvance = false
        rule.setContent { DumplingRingsTheme { content.value() } }
    }
    private fun show(name: String, c: @androidx.compose.runtime.Composable () -> Unit) { content.value = c; shoot(name) }

    @Test fun splash() {
        val c = container(); host()
        show("00_splash") { com.shiostudios.dumplingrings.ui.screens.SplashScreen({}, holdMs = 60_000) }
    }

    @Test fun mainMenu() {
        val c = container(); host()
        show("01_main_menu") { MainMenuScreen(c, Nav(Screen.Menu)) }
        show("26_welcome") { WelcomeScreen(c) {} }
    }

    @Test fun worldMapAndChapter() {
        val c = container(); host()
        show("02_world_map") { WorldMapScreen(c, Nav(Screen.WorldMap(1)), 1) }
        show("03_chapter_1") { ChapterScreen(c, Nav(Screen.Chapter(1)), 1) }
        show("04_world_map_2") { WorldMapScreen(c, Nav(Screen.WorldMap(2)), 2) }
    }

    @Test fun gameplayLevels() {
        val c = container(); host()
        for (idx in listOf(1, 3, 10, 25, 45, 63, 88, 100)) {
            val level = c.content.level(idx)
            val controller = GameController(c, level, true, MainScope())
            show("10_level_%03d".format(idx)) { GameBody(c, rule.activity, controller, "Level $idx", (idx - 1) / 50 + 1, idx == 3, onPause = {}) {} }
        }
    }

    @Test fun worldTransition() {
        val c = container(); host()
        for (idx in listOf(50, 51)) {
            val controller = GameController(c, c.content.level(idx), true, MainScope())
            show("13_transition_$idx") { GameBody(c, rule.activity, controller, "Level $idx", (idx - 1) / 50 + 1, false, onPause = {}) {} }
        }
        val bmp = android.graphics.BitmapFactory.decodeFile("build/screenshots/13_transition_51.png")
        val px = bmp.getPixel(bmp.width / 2, (bmp.height * 0.15).toInt())
        val r = (px shr 16) and 255; val b = px and 255
        org.junit.Assert.assertTrue("level 51 must show the night-market background (dark blue), got r=$r b=$b", b > r)
    }

    @Test fun world2Level() {
        val c = container(); host()
        val controller = GameController(c, c.content.level(63), true, MainScope())
        show("12_level_063_w2") { GameBody(c, rule.activity, controller, "Level 63", 2, false, onPause = {}) {} }
    }

    @Test fun boardLandscapeTablet() {
        val c = container(); host()
        val controller = GameController(c, c.content.level(70), true, MainScope())
        show("11_level_070_landscape") { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(1100.dp, 700.dp)) { GameBody(c, rule.activity, controller, "Level 70", 2, false, onPause = {}) {} } }
    }

    @Test fun metaScreens() {
        val c = container(); host()
        show("20_collection") { CollectionScreen(c, Nav(Screen.Collection)) }
        show("21_shop") { ShopScreen(c, Nav(Screen.Shop), rule.activity) }
        show("22_premium") { PremiumScreen(c, Nav(Screen.Premium), rule.activity) }
        show("23_settings") { SettingsScreen(c, Nav(Screen.Settings), rule.activity) }
        show("24_daily") { DailyScreen(c, Nav(Screen.Daily), rule.activity) }
        show("25_chapter_scene") { ChapterSceneScreen(c, 1) {} }
    }
}
