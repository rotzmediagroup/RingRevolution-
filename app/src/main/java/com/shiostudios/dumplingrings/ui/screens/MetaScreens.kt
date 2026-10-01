package com.shiostudios.dumplingrings.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.R
import com.shiostudios.dumplingrings.core.engine.RuleEngine
import com.shiostudios.dumplingrings.core.generator.Rng
import com.shiostudios.dumplingrings.core.model.LevelDefinition
import com.shiostudios.dumplingrings.core.solver.Solver
import com.shiostudios.dumplingrings.core.systems.Booster
import com.shiostudios.dumplingrings.core.systems.BoosterLedger
import com.shiostudios.dumplingrings.core.systems.DailyPuzzle
import com.shiostudios.dumplingrings.core.systems.DailyRecord
import com.shiostudios.dumplingrings.core.systems.LevelReward
import com.shiostudios.dumplingrings.game.GameController
import com.shiostudios.dumplingrings.platform.PurchaseOutcome
import com.shiostudios.dumplingrings.ui.components.*
import com.shiostudios.dumplingrings.ui.theme.DR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- daily puzzle (offline, deterministic)

/** Build today's puzzle: a campaign template with re-rolled angles, validated by the solver before it is offered. */
suspend fun buildDailyLevel(container: AppContainer, dayId: String, highestCompleted: Int): LevelDefinition = withContext(Dispatchers.Default) {
    val idx = DailyPuzzle.templateIndex(dayId, highestCompleted)
    val base = container.content.level(idx)
    val rng = Rng(DailyPuzzle.seedFor(dayId))
    for (attempt in 0 until 12) {
        val rings = base.rings.map { r -> r.copy(initialAngleDeg = rng.nextInt(360 / r.stepDeg) * r.stepDeg, arc = r.arc?.let { a -> val span = ((a.maxDeg - a.minDeg) % 360 + 360) % 360; val start = rng.nextInt(12) * 30; a.copy(minDeg = start, maxDeg = (start + span) % 360) }) }
        val cand = base.copy(id = "DAILY-$dayId", name = "Daily $dayId", rings = rings, tutorialCueId = null, canonicalSolution = emptyList())
        val engine = runCatching { RuleEngine(cand) }.getOrNull() ?: continue
        val s0 = engine.initialState()
        if ((0 until engine.n).any { engine.canRelease(s0, it) }) continue
        val res = Solver(engine, 150_000, 100_000).solve(s0)
        if (res.solvable) return@withContext cand.copy(parMoves = res.moves, goodMoves = res.moves + maxOf(2, res.moves / 2), canonicalSolution = res.solution.map { Solver.encode(engine, it) })
    }
    base.copy(id = "DAILY-$dayId", name = "Daily $dayId", tutorialCueId = null) // the validated campaign layout itself
}

@Composable
fun DailyScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val dayId = remember { dayId() }
    val record = save.dailyHistory.firstOrNull { it.dayId == dayId }
    val scope = rememberCoroutineScope()
    var level by remember { mutableStateOf<LevelDefinition?>(null) }
    var playing by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<LevelReward?>(null) }
    val unlocked = save.highestCompleted >= 10
    LaunchedEffect(dayId) { if (unlocked && record?.completed != true) level = buildDailyLevel(container, dayId, save.highestCompleted) }
    val lv = level
    if (playing && lv != null) {
        val controller = remember(lv.id) { GameController(container, lv, true, scope) }
        LaunchedEffect(controller.won) {
            if (controller.won && reward == null) {
                val coins = container.content.economy.dailyCoins
                container.save.update { s -> s.copy(coins = s.coins + coins, dailyHistory = (s.dailyHistory.filter { it.dayId != dayId } + DailyRecord(dayId, true, controller.moves, true)).takeLast(400)) }
                container.audio.sfx("level_complete_bell")
                reward = LevelReward(3, coins, emptyMap(), true, false)
            }
        }
        var paused by remember { mutableStateOf(false) }
        GameBody(container, activity, controller, stringResource(R.string.daily_title), 1, save.settings.rotateButtons, onPause = { paused = true }) {
            if (paused) Overlay({ paused = false }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    WoodButton(stringResource(R.string.resume), { paused = false }, Modifier.fillMaxWidth(), color = DR.SakuraDeep)
                    WoodButton(stringResource(R.string.restart), { controller.restart(); paused = false }, Modifier.fillMaxWidth())
                    WoodButton(stringResource(R.string.quit_to_map), { nav.pop() }, Modifier.fillMaxWidth(), color = DR.WoodDark)
                }
            }
            reward?.let { rw -> LevelCompleteOverlay(container, activity, lv, controller.moves, rw, false, onNext = { nav.pop() }, onReplay = { nav.pop() }, onMap = { nav.pop() }) }
        }
        return
    }
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.daily_title), { nav.pop() }) { CoinPill(save.coins) }
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Sprite("sheet10/mystery_basket_f03", Modifier.size(150.dp))
                Text(dayId, style = MaterialTheme.typography.titleLarge, color = DR.WoodDeep)
                Text(stringResource(R.string.daily_streak, dailyStreak(save)), style = MaterialTheme.typography.bodyLarge, color = DR.InkSoft)
                Spacer(Modifier.height(16.dp))
                when {
                    !unlocked -> Text(stringResource(R.string.daily_locked), style = MaterialTheme.typography.bodyLarge, color = DR.Ink)
                    record?.completed == true -> Text(stringResource(R.string.daily_done), style = MaterialTheme.typography.bodyLarge, color = DR.Ink, textAlign = TextAlign.Center)
                    lv == null -> Text("…", style = MaterialTheme.typography.bodyLarge, color = DR.Ink)
                    else -> WoodButton(stringResource(R.string.play) + "  ·  +${container.content.economy.dailyCoins} ⬤", { playing = true }, Modifier.widthIn(max = 360.dp).fillMaxWidth(), color = DR.SakuraDeep, minHeight = 58.dp)
                }
                if (save.premium) {
                    Spacer(Modifier.height(20.dp))
                    val claimed = save.lastPremiumClaimDay == dayId
                    WoodButton(if (claimed) stringResource(R.string.claimed) else stringResource(R.string.claim_daily_booster), {
                        container.save.update { s -> var ns = s; container.content.economy.premiumDailyClaim.forEach { (k, v) -> ns = BoosterLedger.grant(ns, Booster.byId(k) ?: Booster.STEAM_HINT, v, "premium-$dayId-$k").save }; ns.copy(lastPremiumClaimDay = dayId) }
                        container.audio.sfx("coin_chime")
                    }, Modifier.widthIn(max = 360.dp).fillMaxWidth(), enabled = !claimed, color = DR.Indigo)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- collection

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(container: AppContainer, nav: Nav) {
    val save by container.save.state.collectAsState()
    val all = container.content.cosmetics
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.collection_title), { nav.pop() })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                for ((kind, title) in listOf("character" to R.string.characters, "theme" to R.string.themes, "decor" to R.string.decor)) {
                    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = DR.WoodDeep, modifier = Modifier.padding(vertical = 8.dp))
                    val items = all.filter { it.kind == kind }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { }
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (c in items) {
                            val unlocked = c.id in save.unlockedCosmetics || (c.unlockPremium && save.premium) || (c.unlockStars?.let { save.totalStars >= it } ?: false)
                            val selected = kind == "theme" && save.selectedTheme == c.id
                            Column(Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) DR.Sakura.copy(alpha = 0.5f) else DR.CreamDark)
                                .clickable(enabled = unlocked && kind == "theme") { container.save.update { it.copy(selectedTheme = if (it.selectedTheme == c.id) "default" else c.id) }; container.audio.sfx("button_tap") }
                                .padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                                    if (c.sprite.startsWith("materials/") || c.sprite.startsWith("3d/")) AssetImage(c.sprite + ".webp", Modifier.size(64.dp).clip(RoundedCornerShape(50)).alpha(if (unlocked) 1f else 0.3f))
                                    else Sprite(c.sprite, Modifier.size(78.dp).alpha(if (unlocked) 1f else 0.25f))
                                    if (!unlocked) Glyph("lock", Modifier.size(26.dp), DR.WoodDeep)
                                }
                                Text(stringResource(cosmeticNameRes(container, c.nameKey)), style = MaterialTheme.typography.labelMedium, color = DR.Ink, textAlign = TextAlign.Center, maxLines = 2)
                                if (!unlocked) Text(c.unlockChapter?.let { stringResource(R.string.unlock_by_chapter, it) } ?: c.unlockStars?.let { stringResource(R.string.unlock_by_stars, it) } ?: stringResource(R.string.unlock_by_premium), style = MaterialTheme.typography.labelMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)), color = DR.InkSoft, textAlign = TextAlign.Center)
                                else if (kind == "theme") Text(stringResource(if (selected) R.string.selected else R.string.select), style = MaterialTheme.typography.labelMedium, color = DR.SakuraDeep)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- shop (coins -> boosters only; no lootboxes)

@Composable
fun ShopScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val eco = container.content.economy
    var offer by remember { mutableStateOf<Booster?>(null) }
    Box(Modifier.fillMaxSize().background(DR.Cream)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.shop), { nav.pop() }) { CoinPill(save.coins) }
            Column(Modifier.padding(16.dp).widthIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for ((b, sprite, nameRes, descRes) in listOf(
                    Quad(Booster.STEAM_HINT, "sheet16/tea_cup_bamboo_steam", R.string.hint, R.string.booster_hint_desc),
                    Quad(Booster.STEAM_PEEK, "sheet05/icon_lantern_sakura", R.string.steam_peek, R.string.booster_peek_desc),
                    Quad(Booster.CHEFS_TWIST, "sheet14/booster_swirl_idle", R.string.chefs_twist, R.string.booster_twist_desc),
                    Quad(Booster.GOLDEN_STEAMER, "sheet08/golden_dumpling_idle", R.string.golden_steamer, R.string.booster_golden_desc),
                )) {
                    val price = eco.prices[b.id] ?: 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(DR.CreamDark).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sprite(sprite, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(nameRes) + "  ×" + (save.inventory[b.id] ?: 0), style = MaterialTheme.typography.titleLarge, color = DR.WoodDeep)
                            Text(stringResource(descRes), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                        }
                        Spacer(Modifier.width(8.dp))
                        WoodButton(stringResource(R.string.buy_for, price), {
                            val r = BoosterLedger.buy(save, b, eco)
                            if (r.applied) { container.save.update { r.save }; container.audio.sfx("coin_chime") } else offer = b
                        }, color = DR.GoldDeep, minHeight = 44.dp, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(stringResource(R.string.offline_note), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                if (!save.premium) WoodButton(stringResource(R.string.premium), { nav.push(com.shiostudios.dumplingrings.Screen.Premium) }, Modifier.fillMaxWidth(), color = DR.Indigo)
            }
        }
    }
    offer?.let { b -> BoosterOfferDialog(container, activity, b, stringResource(when (b) { Booster.STEAM_HINT -> R.string.hint; Booster.STEAM_PEEK -> R.string.steam_peek; Booster.CHEFS_TWIST -> R.string.chefs_twist; Booster.GOLDEN_STEAMER -> R.string.golden_steamer })) { offer = null } }
}

private data class Quad(val b: Booster, val sprite: String, val name: Int, val desc: Int)

// ---------------------------------------------------------------- premium (one-time, no subscription)

@Composable
fun PremiumScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val ent by container.purchases.state.collectAsState()
    var msg by remember { mutableStateOf<String?>(null) }
    val unavailable = stringResource(R.string.store_unavailable)
    Box(Modifier.fillMaxSize().background(DR.Indigo)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.premium_title), { nav.pop() }, titleColor = DR.Cream)
            Column(Modifier.fillMaxWidth().padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                WoodPanel(Modifier.widthIn(max = 480.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Sprite("sheet06/cloud_dragon_cheer", Modifier.size(140.dp))
                        Text(stringResource(R.string.premium_pitch), style = MaterialTheme.typography.bodyLarge, color = DR.Ink, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        AssetImage("materials/ube_purple.webp", Modifier.size(72.dp).clip(RoundedCornerShape(50)))
                        Spacer(Modifier.height(14.dp))
                        if (save.premium || ent.premium) Text(stringResource(R.string.premium_active), style = MaterialTheme.typography.titleLarge, color = DR.Bamboo, textAlign = TextAlign.Center)
                        else {
                            WoodButton(stringResource(R.string.buy_premium, ent.priceText ?: ""), {
                                if (!container.purchases.available) { msg = unavailable; return@WoodButton }
                                container.purchases.buyPremium(activity) { r ->
                                    when (r) {
                                        PurchaseOutcome.Success -> { container.save.update { it.copy(premium = true, premiumVerifiedAt = System.currentTimeMillis(), unlockedCosmetics = (it.unlockedCosmetics + "theme_premium_ube").distinct()) }; container.audio.sfx("chapter_bell") }
                                        PurchaseOutcome.Cancelled -> {}
                                        PurchaseOutcome.Pending -> msg = "…"
                                        is PurchaseOutcome.Failed -> msg = unavailable
                                    }
                                }
                            }, Modifier.fillMaxWidth(), color = DR.SakuraDeep, minHeight = 58.dp, enabled = container.purchases.available)
                            Spacer(Modifier.height(8.dp))
                            WoodButton(stringResource(R.string.restore_purchases), { container.purchases.restore { ok -> if (ok) container.save.update { it.copy(premium = true) } else msg = unavailable } }, Modifier.fillMaxWidth(), enabled = container.purchases.available)
                            if (!container.purchases.available) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.store_unavailable), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft) }
                        }
                        msg?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft) }
                    }
                }
            }
        }
    }
}
