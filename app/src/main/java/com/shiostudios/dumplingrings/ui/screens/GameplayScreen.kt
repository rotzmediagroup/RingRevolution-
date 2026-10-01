package com.shiostudios.dumplingrings.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.R
import com.shiostudios.dumplingrings.Screen
import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.systems.Booster
import com.shiostudios.dumplingrings.core.systems.BoosterLedger
import com.shiostudios.dumplingrings.core.systems.LevelReward
import com.shiostudios.dumplingrings.core.systems.Progression
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.platform.RewardResult
import com.shiostudios.dumplingrings.ui.components.*
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay

fun tutorialRes(cue: String?): Int? = when (cue) {
    "rotate_first_ring" -> R.string.tut_rotate_first_ring; "gap_and_exit" -> R.string.tut_gap_and_exit; "two_rings_order" -> R.string.tut_two_rings_order
    "free_rotation" -> R.string.tut_free_rotation; "recognise_blocker" -> R.string.tut_recognise_blocker; "undo" -> R.string.tut_undo; "restart" -> R.string.tut_restart
    "simple_chain" -> R.string.tut_simple_chain; "hint_optional" -> R.string.tut_hint_optional; "first_finale" -> R.string.tut_first_finale; else -> null
}

@Composable
fun GameplayScreen(container: AppContainer, nav: Nav, activity: ComponentActivity, levelIndex: Int, backRequested: Int) {
    val level = remember(levelIndex) { container.content.level(levelIndex) }
    val scope = rememberCoroutineScope()
    val controller = remember(levelIndex) { GameController(container, level, false, scope) }
    val world = container.content.worldOf(levelIndex)
    val chapter = container.content.chapterOf(levelIndex)
    var reward by remember(levelIndex) { mutableStateOf<LevelReward?>(null) }
    var chapterJustCompleted by remember(levelIndex) { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var lastBack by remember { mutableStateOf(backRequested) }
    LaunchedEffect(backRequested) { if (backRequested != lastBack) { lastBack = backRequested; if (reward == null) paused = !paused else nav.popTo { it is Screen.Chapter || it is Screen.WorldMap || it is Screen.Menu } } }

    LaunchedEffect(controller.won) {
        if (controller.won && reward == null) {
            delay(650)
            val eco = container.content.economy
            val wasChapterDone = container.save.current.chapterComplete(level.chapter)
            var r: LevelReward? = null
            container.save.update { s ->
                val (ns, rw) = Progression.completeLevel(s, level.id, controller.moves, level.parMoves, level.goodMoves, level.reward.coins, level.reward.boosters, eco)
                r = rw; ns
            }
            val nowDone = container.save.current.chapterComplete(level.chapter)
            if (!wasChapterDone && nowDone) {
                chapterJustCompleted = true
                val ch = chapter
                container.save.update { s ->
                    val inv = s.inventory.toMutableMap(); ch.rewardBoosters.forEach { (k, v) -> inv[k] = (inv[k] ?: 0) + v }
                    val cos = (s.unlockedCosmetics + ch.rewardCosmetic + characterCosmeticId(container, ch.rewardCharacter)).distinct()
                    s.copy(coins = s.coins + ch.rewardCoins, inventory = inv, unlockedCosmetics = cos)
                }
            }
            container.audio.sfx("level_complete_bell")
            reward = r
        }
    }

    val save by container.save.state.collectAsState()
    val title = stringResource(R.string.level_n, levelIndex)
    GameBody(container, activity, controller, title, world.number, save.settings.rotateButtons, onPause = { paused = true }) {
        // overlays
        if (paused) PauseOverlay(container, onResume = { paused = false }, onRestart = { controller.restart(); paused = false }, onQuit = { nav.popTo { it is Screen.Chapter || it is Screen.WorldMap || it is Screen.Menu } }, onSettings = { nav.push(Screen.Settings) })
        reward?.let { rw ->
            LevelCompleteOverlay(container, activity, level, controller.moves, rw, chapterJustCompleted,
                onNext = {
                    val next = levelIndex + 1
                    val proceed: () -> Unit = {
                        when {
                            next > 150 -> nav.popTo { it is Screen.Menu }
                            chapterJustCompleted && levelIndex % 50 == 0 -> nav.replace(Screen.ChapterScene(level.chapter, Screen.WorldIntro(world.number + 1, Screen.WorldMap(world.number + 1))))
                            chapterJustCompleted -> nav.replace(Screen.ChapterScene(level.chapter, Screen.Play(next)))
                            else -> nav.replace(Screen.Play(next))
                        }
                    }
                    if (!container.ads2.maybeInterstitial(activity, levelIndex, level.isChefLevel, false, proceed)) proceed()
                },
                onReplay = { nav.replace(Screen.Play(levelIndex)) },
                onMap = { nav.popTo { it is Screen.Chapter || it is Screen.WorldMap || it is Screen.Menu } })
        }
    }
}

/** Shared gameplay layout for campaign and daily levels: portrait = column, landscape/tablet = board + side panel. */
@Composable
fun GameBody(container: AppContainer, activity: ComponentActivity, controller: GameController, title: String, world: Int, rotateButtons: Boolean, onPause: () -> Unit, overlays: @Composable () -> Unit) {
    val save by container.save.state.collectAsState()
    val level = controller.level
    val reduce = LocalReduceMotion.current
    var explain by remember { mutableStateOf<String?>(null) }
    val themeMaterial = save.selectedTheme.takeIf { it != "default" }?.let { when (it) { "theme_matcha" -> "dough_matcha"; "theme_beet" -> "dough_beet"; "theme_gold" -> "dough_gold"; "theme_premium_ube" -> "dough_ube"; "theme_bronze" -> "ring_bronze"; "theme_obsidian" -> "ring_obsidian"; "theme_pearl" -> "ring_pearl"; "theme_lacquer" -> "ring_lacquer"; "theme_gold_rope" -> "ring_gold_rope"; "theme_pearl_beaded" -> "ring_pearl_beaded"; "theme_jade_bamboo" -> "ring_jade_bamboo"; "theme_silver_wave" -> "ring_silver_wave"; else -> null } }
    val stuck = controller.failedReleaseTaps >= 4 || (level.parMoves > 0 && controller.moves > level.parMoves * 3 + 4)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val bg = com.shiostudios.dumplingrings.assets.AssetCatalog.backgroundFor(world, landscape)
        com.shiostudios.dumplingrings.ui.fx.AnimatedSceneBackground(bg, world, save.settings.reduceMotion, save.settings.effectQuality) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                if (!landscape) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(container, controller, title, onPause)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Board(controller, save.settings.reduceMotion, save.settings.highContrast, save.settings.effectQuality, themeMaterial, world, Modifier.fillMaxSize())
                        }
                        StatusLine(controller, explain, stuck)
                        Controls(container, activity, controller, rotateButtons, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) { explain = it }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Board(controller, save.settings.reduceMotion, save.settings.highContrast, save.settings.effectQuality, themeMaterial, world, Modifier.fillMaxSize())
                        }
                        Column(Modifier.width(300.dp).fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            TopBar(container, controller, title, onPause, compact = true)
                            StatusLine(controller, explain, stuck)
                            Controls(container, activity, controller, rotateButtons, Modifier.fillMaxWidth(), vertical = true) { explain = it }
                        }
                    }
                }
                // combo toast
                AnimatedVisibility(controller.combo >= 2 && controller.releases.isNotEmpty(), Modifier.align(Alignment.TopCenter).padding(top = 90.dp), enter = fadeIn(), exit = fadeOut()) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(DR.GoldDeep.copy(alpha = 0.92f)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.combo, controller.combo), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    }
                }
                overlays()
            }
        }
    }
}

@Composable
private fun Board(controller: GameController, reduce: Boolean, hc: Boolean, quality: String, themeMaterial: String?, world: Int, modifier: Modifier) {
    Box(modifier) {
        com.shiostudios.dumplingrings.ui.board3d.Board3D(controller, Modifier.fillMaxSize(), reduce, hc, quality, themeMaterial, world)
    }
}

@Composable
private fun TopBar(container: AppContainer, controller: GameController, title: String, onPause: () -> Unit, compact: Boolean = false) {
    val save by container.save.state.collectAsState()
    val level = controller.level
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(contentDescription = stringResource(R.string.pause), onClick = onPause) { Glyph("pause", Modifier.size(24.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DR.WoodDeep.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Text(title + (if (level.isChefLevel) " ★" else ""), style = MaterialTheme.typography.titleLarge, color = DR.Cream)
            val par = if (level.parMoves > 0) "${stringResource(R.string.moves)} ${controller.moves} · ${stringResource(R.string.par)} ${level.parMoves}" else "${stringResource(R.string.moves)} ${controller.moves}"
            Text(par, style = MaterialTheme.typography.labelMedium, color = DR.Cream.copy(alpha = 0.9f))
        }
        if (!compact) CoinPill(save.coins)
    }
}

@Composable
private fun StatusLine(controller: GameController, explain: String?, stuck: Boolean) {
    val level = controller.level
    val tut = tutorialRes(level.tutorialCueId)?.let { stringResource(it) }
    val intro = if (level.chapterLevel == 1 && level.index > 10) level.mechanicTags.lastOrNull { mechanicIntroRes(it) != null }?.let { stringResource(mechanicIntroRes(it)!!) } else null
    val text = explain ?: (if (stuck) stringResource(R.string.stuck_tip) else null) ?: tut ?: intro
    AnimatedVisibility(text != null, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(14.dp)).background(DR.WoodDeep.copy(alpha = 0.78f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(text ?: "", style = MaterialTheme.typography.bodyMedium, color = DR.Cream, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Controls(container: AppContainer, activity: ComponentActivity, controller: GameController, rotateButtons: Boolean, modifier: Modifier, vertical: Boolean = false, onExplain: (String?) -> Unit) {
    val save by container.save.state.collectAsState()
    val inv = save.inventory
    val blockedRing = stringResource(R.string.blocked_by_ring); val blockedLock = stringResource(R.string.blocked_by_lock); val blockedObs = stringResource(R.string.blocked_by_obstacle)
    LaunchedEffect(controller.failedReleaseTaps, controller.selected) {
        val s = controller.selected
        if (s != null && controller.failedReleaseTaps > 0) {
            val why = controller.explain(s)
            onExplain(when { why.any { it.startsWith("lock") } -> blockedLock; why.any { it.startsWith("obstacle") } -> blockedObs; why.isNotEmpty() -> blockedRing; else -> null })
            delay(4000); onExplain(null)
        }
    }
    val content: @Composable () -> Unit = {
        RoundIconButton(contentDescription = stringResource(R.string.undo), onClick = { controller.undo() }, color = DR.Wood, sfx = "undo") { Glyph("undo", Modifier.size(26.dp)) }
        RoundIconButton(contentDescription = stringResource(R.string.restart), onClick = { controller.restart() }, color = DR.Wood) { Glyph("restart", Modifier.size(26.dp)) }
        BoosterButton(container, activity, controller, Booster.STEAM_HINT, "sheet16/tea_cup_bamboo_steam", inv["hint"] ?: 0, R.string.hint)
        BoosterButton(container, activity, controller, Booster.STEAM_PEEK, "sheet05/icon_lantern_sakura", inv["steam_peek"] ?: 0, R.string.steam_peek)
        BoosterButton(container, activity, controller, Booster.CHEFS_TWIST, "sheet14/booster_swirl_idle", inv["chefs_twist"] ?: 0, R.string.chefs_twist)
        BoosterButton(container, activity, controller, Booster.GOLDEN_STEAMER, "sheet08/golden_dumpling_idle", inv["golden_steamer"] ?: 0, R.string.golden_steamer)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (rotateButtons) {
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RoundIconButton(contentDescription = stringResource(R.string.cd_rotate_left), onClick = { controller.rotateSelected(false) }, size = 64.dp, color = DR.Bamboo) { Glyph("left", Modifier.size(30.dp)) }
                RoundIconButton(contentDescription = stringResource(R.string.cd_rotate_right), onClick = { controller.rotateSelected(true) }, size = 64.dp, color = DR.Bamboo) { Glyph("right", Modifier.size(30.dp)) }
            }
        }
        if (vertical) Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { content() } }
        else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun BoosterButton(container: AppContainer, activity: ComponentActivity, controller: GameController, b: Booster, sprite: String, count: Int, nameRes: Int) {
    val name = stringResource(nameRes)
    var offer by remember { mutableStateOf(false) }
    Box {
        Box(contentAlignment = Alignment.TopEnd) {
            RoundIconButton(sprite, name, onClick = {
                if (count > 0) controller.useBooster(b) { } else offer = true
            }, color = if (count > 0) DR.GoldDeep else DR.WoodDark)
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(50)).background(if (count > 0) DR.SakuraDeep else DR.WoodDeep), contentAlignment = Alignment.Center) {
                Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
        }
    }
    if (offer) BoosterOfferDialog(container, activity, b, name) { offer = false }
}

/** Empty inventory: offer coins purchase, and a rewarded ad only when ads are enabled and consent given. */
@Composable
fun BoosterOfferDialog(container: AppContainer, activity: ComponentActivity, b: Booster, name: String, onClose: () -> Unit) {
    val save by container.save.state.collectAsState()
    val eco = container.content.economy
    val price = eco.prices[b.id] ?: 0
    var adMsg by remember { mutableStateOf<String?>(null) }
    val adUnavailable = stringResource(R.string.ad_unavailable)
    Overlay(onClose) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TitleText(name, size = 24)
            Text(stringResource(when (b) { Booster.STEAM_HINT -> R.string.booster_hint_desc; Booster.STEAM_PEEK -> R.string.booster_peek_desc; Booster.CHEFS_TWIST -> R.string.booster_twist_desc; Booster.GOLDEN_STEAMER -> R.string.booster_golden_desc }),
                style = MaterialTheme.typography.bodyMedium, color = DR.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.booster_empty), style = MaterialTheme.typography.labelLarge, color = DR.InkSoft)
            Spacer(Modifier.height(8.dp))
            WoodButton(stringResource(R.string.buy_for, price) + " ⬤", {
                val r = BoosterLedger.buy(save, b, eco)
                if (r.applied) { container.save.update { r.save }; container.audio.sfx("coin_chime"); onClose() } else container.audio.sfx("invalid")
            }, Modifier.fillMaxWidth(), enabled = save.coins >= price, color = DR.GoldDeep)
            if (container.ads2.rewardedOffered) {
                Spacer(Modifier.height(8.dp))
                WoodButton(stringResource(R.string.watch_ad_for) + " " + name, {
                    container.ads2.showRewarded(activity, grant = { id -> container.save.update { BoosterLedger.grant(it, b, 1, id).save } }) { r ->
                        if (r is RewardResult.Earned) { container.audio.sfx("coin_chime"); onClose() } else if (r is RewardResult.Failed) adMsg = adUnavailable
                    }
                }, Modifier.fillMaxWidth(), color = DR.Indigo)
            }
            adMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(10.dp))
            WoodButton(stringResource(R.string.close), onClose, Modifier.fillMaxWidth(), color = DR.Wood)
        }
    }
}

@Composable
fun Overlay(onDismiss: (() -> Unit)?, content: @Composable () -> Unit) {
    val src1 = remember { MutableInteractionSource() }; val src2 = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)).let { m -> if (onDismiss != null) m.clickable(src1, null) { onDismiss() } else m }, contentAlignment = Alignment.Center) {
        Box(Modifier.padding(20.dp).widthIn(max = 460.dp).clickable(src2, null) {}) {
            WoodPanel(content = { content() })
        }
    }
}

@Composable
private fun PauseOverlay(container: AppContainer, onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit, onSettings: () -> Unit) {
    val save by container.save.state.collectAsState()
    Overlay(onResume) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TitleText(stringResource(R.string.pause), size = 28)
            WoodButton(stringResource(R.string.resume), onResume, Modifier.fillMaxWidth(), color = DR.SakuraDeep)
            WoodButton(stringResource(R.string.restart), onRestart, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WoodButton(stringResource(R.string.music), { container.save.update { it.copy(settings = it.settings.copy(music = !it.settings.music)) } }, Modifier.weight(1f), color = if (save.settings.music) DR.Bamboo else DR.WoodDark, minHeight = 44.dp)
                WoodButton(stringResource(R.string.sfx), { container.save.update { it.copy(settings = it.settings.copy(sfx = !it.settings.sfx)) } }, Modifier.weight(1f), color = if (save.settings.sfx) DR.Bamboo else DR.WoodDark, minHeight = 44.dp)
            }
            WoodButton(stringResource(R.string.settings), onSettings, Modifier.fillMaxWidth())
            WoodButton(stringResource(R.string.quit_to_map), onQuit, Modifier.fillMaxWidth(), color = DR.WoodDark)
        }
    }
}

@Composable
fun LevelCompleteOverlay(container: AppContainer, activity: ComponentActivity, level: LevelDefinition, moves: Int, rw: LevelReward, chapterDone: Boolean, onNext: () -> Unit, onReplay: () -> Unit, onMap: () -> Unit) {
    val reduce = LocalReduceMotion.current
    var shownStars by remember { mutableStateOf(0) }
    var doubled by remember { mutableStateOf(false) }
    var adMsg by remember { mutableStateOf<String?>(null) }
    val adUnavailable = stringResource(R.string.ad_unavailable)
    LaunchedEffect(Unit) {
        for (i in 1..rw.stars) { delay(if (reduce) 0 else 350); shownStars = i; container.audio.sfx("star_$i") }
        delay(200); if (rw.coins > 0) container.audio.sfx("coin_chime")
    }
    val scale by animateFloatAsState(if (shownStars > 0 || reduce) 1f else 0.9f, tween(300), label = "pop")
    val reaction = remember(level.id) { listOf("sheet02/bao_cheer", "sheet05/bao_steamer_cheer", "sheet08/golden_dumpling_cheer", "sheet05/dango_cheer", "sheet02/tea_cup_cheer")[level.index % 5] }
    Overlay(null) {
        Column(Modifier.scale(scale), horizontalAlignment = Alignment.CenterHorizontally) {
            TitleText(stringResource(if (level.isChefLevel) R.string.chef_complete else R.string.level_complete), size = 26)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sprite(reaction, Modifier.size(90.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    StarRow(shownStars, size = 34.dp)
                    Text("${stringResource(R.string.moves)}: $moves  ·  ${stringResource(R.string.par)}: ${level.parMoves}", style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                    if (rw.stars < 3) Text(stringResource(R.string.in_par), style = MaterialTheme.typography.labelMedium, color = DR.InkSoft)
                    if (rw.improvedStars && !rw.firstClear) Text(stringResource(R.string.new_record), style = MaterialTheme.typography.labelLarge, color = DR.SakuraDeep)
                }
            }
            Spacer(Modifier.height(6.dp))
            if (rw.coins > 0) Text(stringResource(R.string.coins_earned, if (doubled) rw.coins * 2 else rw.coins), style = MaterialTheme.typography.headlineSmall, color = DR.GoldDeep)
            rw.boosters.forEach { (k, v) -> Text("+$v " + stringResource(when (k) { "hint" -> R.string.hint; "steam_peek" -> R.string.steam_peek; "chefs_twist" -> R.string.chefs_twist; else -> R.string.golden_steamer }), style = MaterialTheme.typography.bodyLarge, color = DR.Ink) }
            if (chapterDone) Text(stringResource(R.string.chapter_complete), style = MaterialTheme.typography.titleLarge, color = DR.SakuraDeep)
            Spacer(Modifier.height(12.dp))
            WoodButton(stringResource(R.string.next_level), onNext, Modifier.fillMaxWidth(), color = DR.SakuraDeep, minHeight = 56.dp)
            Spacer(Modifier.height(8.dp))
            if (container.ads2.rewardedOffered && !doubled && rw.coins > 0) {
                WoodButton(stringResource(R.string.double_reward_ad), {
                    container.ads2.showRewarded(activity, grant = { id -> container.save.update { BoosterLedger.grantCoins(it, rw.coins, id).save } }) { r ->
                        if (r is RewardResult.Earned) { doubled = true; container.audio.sfx("coin_chime") } else if (r is RewardResult.Failed) adMsg = adUnavailable
                    }
                }, Modifier.fillMaxWidth(), color = DR.Indigo)
                Spacer(Modifier.height(8.dp))
            }
            adMsg?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft, textAlign = TextAlign.Center); Spacer(Modifier.height(6.dp)) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WoodButton(stringResource(R.string.replay), onReplay, Modifier.weight(1f))
                WoodButton(stringResource(R.string.quit_to_map), onMap, Modifier.weight(1f), color = DR.WoodDark)
            }
        }
    }
}
