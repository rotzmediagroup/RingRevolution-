package com.shiostudios.dumplingrings.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.BuildConfig
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.R
import com.shiostudios.dumplingrings.Screen
import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.systems.Settings
import com.shiostudios.dumplingrings.ui.components.*
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion

@Composable
fun WelcomeScreen(container: AppContainer, onDone: () -> Unit) {
    SceneBackground("menu/keyart.webp", dim = 0.25f) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            WoodPanel(Modifier.widthIn(max = 480.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Sprite("sheet12/dumpling_cheer_f01", Modifier.size(96.dp))
                    TitleText(stringResource(R.string.welcome_title), size = 26)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.welcome_text), style = MaterialTheme.typography.bodyLarge, color = DR.Ink, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.offline_note), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    WoodButton(stringResource(R.string.lets_go), onDone, Modifier.fillMaxWidth(), color = DR.SakuraDeep)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MainMenuScreen(container: AppContainer, nav: Nav) {
    val save by container.save.state.collectAsState()
    val reduce = LocalReduceMotion.current
    val inf = rememberInfiniteTransition(label = "menu")
    val bob by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "bob")
    val next = (save.highestCompleted + 1).coerceAtMost(150)
    val hasProgress = save.highestCompleted > 0 || save.inProgress != null
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        SceneBackground("menu/keyart.webp") {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to DR.WoodDeep.copy(alpha = 0.75f))))
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    CoinPill(save.coins)
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(contentDescription = stringResource(R.string.settings), onClick = { nav.push(Screen.Settings) }) { Glyph("gear", Modifier.size(26.dp)) }
                }
                Spacer(Modifier.height(if (landscape) 4.dp else 24.dp))
                Box(Modifier.scale(if (reduce) 1f else 1f + bob * 0.02f)) { TitleText("Dumpling Rings", size = if (landscape) 36 else 44, color = DR.Cream) }
                Text("Shio Studios", style = MaterialTheme.typography.labelLarge, color = DR.Cream.copy(alpha = 0.85f))
                Spacer(Modifier.weight(1f))
                Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WoodButton(if (hasProgress) stringResource(R.string.continue_) + "  ·  " + stringResource(R.string.level_n, next) else stringResource(R.string.play),
                        { nav.push(Screen.WorldMap(LevelCodec.worldNumber(next))); nav.push(Screen.Play(next)) }, Modifier.fillMaxWidth(), color = DR.SakuraDeep, minHeight = 60.dp,
                        style = MaterialTheme.typography.headlineSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WoodButton(stringResource(R.string.world_map), { nav.push(Screen.WorldMap(LevelCodec.worldNumber(next))) }, Modifier.weight(1f))
                        WoodButton(stringResource(R.string.collection), { nav.push(Screen.Collection) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WoodButton(stringResource(R.string.daily_puzzle), { nav.push(Screen.Daily) }, Modifier.weight(1f), color = DR.Bamboo)
                        WoodButton(stringResource(R.string.shop), { nav.push(Screen.Shop) }, Modifier.weight(1f), color = DR.GoldDeep)
                    }
                    if (!save.premium) WoodButton(stringResource(R.string.premium), { nav.push(Screen.Premium) }, Modifier.fillMaxWidth(), color = DR.Indigo)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun SettingsScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val s = save.settings
    fun set(f: (Settings) -> Settings) = container.save.update { it.copy(settings = f(it.settings)) }
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.settings), { nav.pop() })
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToggleRow(stringResource(R.string.music), s.music) { set { it.copy(music = !it.music) } }
                SliderRow(stringResource(R.string.music), s.musicVolume, s.music) { v -> set { it.copy(musicVolume = v) } }
                ToggleRow(stringResource(R.string.sfx), s.sfx) { set { it.copy(sfx = !it.sfx) } }
                SliderRow(stringResource(R.string.sfx), s.sfxVolume, s.sfx) { v -> set { it.copy(sfxVolume = v) } }
                ToggleRow(stringResource(R.string.haptics), s.haptics) { set { it.copy(haptics = !it.haptics) } }
                Divider(Modifier.padding(vertical = 6.dp))
                ToggleRow(stringResource(R.string.reduce_motion), s.reduceMotion) { set { it.copy(reduceMotion = !it.reduceMotion) } }
                ToggleRow(stringResource(R.string.high_contrast), s.highContrast) { set { it.copy(highContrast = !it.highContrast) } }
                ToggleRow(stringResource(R.string.rotate_buttons), s.rotateButtons) { set { it.copy(rotateButtons = !it.rotateButtons) } }
                Text(stringResource(R.string.effect_quality), style = MaterialTheme.typography.titleMedium, color = DR.Ink, modifier = Modifier.padding(top = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, label) in listOf("low" to R.string.quality_low, "medium" to R.string.quality_medium, "high" to R.string.quality_high))
                        WoodButton(stringResource(label), { set { it.copy(effectQuality = id) } }, Modifier.weight(1f), color = if (s.effectQuality == id) DR.SakuraDeep else DR.Wood, minHeight = 44.dp)
                }
                Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium, color = DR.Ink, modifier = Modifier.padding(top = 6.dp))
                val langs = listOf("system" to stringResource(R.string.lang_system), "en" to "English", "nl" to "Nederlands", "de" to "Deutsch")
                for (row in langs.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((id, label) in row)
                        WoodButton(label, { set { it.copy(language = id) }; applyLanguage(id) }, Modifier.weight(1f), color = if (s.language == id) DR.SakuraDeep else DR.Wood, minHeight = 44.dp)
                }
                Divider(Modifier.padding(vertical = 6.dp))
                WoodButton(stringResource(R.string.privacy), { nav.push(Screen.Privacy) }, Modifier.fillMaxWidth(), color = DR.Indigo)
                WoodButton(stringResource(R.string.stats), { nav.push(Screen.Stats) }, Modifier.fillMaxWidth())
                WoodButton(stringResource(R.string.credits), { nav.push(Screen.Credits) }, Modifier.fillMaxWidth())
                if (!save.premium) WoodButton(stringResource(R.string.restore_purchases), { container.purchases.restore { ok -> if (ok) container.save.update { it.copy(premium = true) } } }, Modifier.fillMaxWidth(), enabled = container.purchases.available)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

fun applyLanguage(id: String) {
    val locales = if (id == "system") androidx.core.os.LocaleListCompat.getEmptyLocaleList() else androidx.core.os.LocaleListCompat.forLanguageTags(id)
    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onToggle() }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = DR.Ink, modifier = Modifier.weight(1f))
        Switch(value, { onToggle() }, colors = SwitchDefaults.colors(checkedTrackColor = DR.Bamboo, checkedThumbColor = Color.White, uncheckedTrackColor = DR.CreamDark, uncheckedThumbColor = DR.Wood))
    }
}

@Composable
private fun SliderRow(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).alpha(if (enabled) 1f else 0.4f), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Slider(value, onChange, Modifier.weight(1f), enabled = enabled, colors = SliderDefaults.colors(thumbColor = DR.SakuraDeep, activeTrackColor = DR.Sakura, inactiveTrackColor = DR.CreamDark))
    }
}

@Composable
fun PrivacyScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.privacy), { nav.pop() })
            Column(Modifier.padding(20.dp).widthIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.privacy_text), style = MaterialTheme.typography.bodyLarge, color = DR.Ink)
                if (!BuildConfig.ADS_ENABLED) Text(stringResource(R.string.ads_disabled_build), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                else {
                    Text(stringResource(R.string.consent_text), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                    WoodButton(stringResource(R.string.consent_manage), { container.consent.showPrivacyOptions(activity) }, Modifier.fillMaxWidth(), enabled = container.consent.privacyOptionsRequired || true)
                }
            }
        }
    }
}

@Composable
fun CreditsScreen(container: AppContainer, nav: Nav) {
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.credits), { nav.pop() })
            Column(Modifier.padding(20.dp).widthIn(max = 560.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Sprite("sheet01/crane_cheer", Modifier.size(140.dp))
                Text(stringResource(R.string.credits_text), style = MaterialTheme.typography.bodyLarge, color = DR.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("v" + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.labelMedium, color = DR.InkSoft)
            }
        }
    }
}

@Composable
fun StatsScreen(container: AppContainer, nav: Nav) {
    val save by container.save.state.collectAsState()
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.stats), { nav.pop() })
            Column(Modifier.padding(20.dp).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow(stringResource(R.string.stat_levels), "${save.levels.count { it.value.completed }} / 150")
                StatRow(stringResource(R.string.stat_stars), "${save.totalStars} / 450")
                StatRow(stringResource(R.string.stat_moves), save.totalMoves.toString())
                StatRow(stringResource(R.string.stat_releases), save.totalReleases.toString())
                StatRow(stringResource(R.string.stat_best_combo), save.bestCombo.toString())
                StatRow(stringResource(R.string.daily_streak, dailyStreak(save)), "")
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DR.CreamDark).padding(14.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = DR.Ink, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = DR.WoodDeep)
    }
}

/** Local calendar day id (yyyy-MM-dd) without java.time (minSdk 24). */
fun dayId(offsetDays: Int = 0): String {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DAY_OF_YEAR, offsetDays)
    return "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
}

fun dailyStreak(save: com.shiostudios.dumplingrings.core.systems.SaveData): Int {
    val done = save.dailyHistory.filter { it.completed }.map { it.dayId }.toSet()
    var streak = 0
    var offset = 0
    if (dayId(0) !in done) offset = -1
    while (dayId(offset) in done && streak < 400) { streak++; offset-- }
    return streak
}
