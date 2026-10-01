package com.shiostudios.dumplingrings.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.R
import com.shiostudios.dumplingrings.Screen
import com.shiostudios.dumplingrings.content.ChapterDef
import com.shiostudios.dumplingrings.core.content.LevelCodec
import com.shiostudios.dumplingrings.ui.components.*
import com.shiostudios.dumplingrings.ui.theme.DR
import com.shiostudios.dumplingrings.ui.theme.LocalWorldPalette
import kotlinx.coroutines.delay

fun worldNameRes(world: Int) = when (world) { 1 -> R.string.world_1_name; 2 -> R.string.world_2_name; else -> R.string.world_3_name }
fun mechanicNameRes(m: String): Int = when (m) {
    "basic_rotation" -> R.string.mech_basic_rotation; "overlap" -> R.string.mech_overlap; "locked_order" -> R.string.mech_locked_order; "nested" -> R.string.mech_nested
    "combo" -> R.string.mech_combo; "dense_overlap" -> R.string.mech_dense_overlap; "colour_gate" -> R.string.mech_colour_gate; "restricted_arc" -> R.string.mech_restricted_arc
    "rotation_chain" -> R.string.mech_rotation_chain; "rotating_obstacle" -> R.string.mech_rotating_obstacle; "complex_sequence" -> R.string.mech_complex_sequence
    "double_gap" -> R.string.mech_double_gap; "hinge_link" -> R.string.mech_hinge_link; "all_mechanics" -> R.string.mech_all_mechanics; else -> R.string.mech_finale
}
fun mechanicIntroRes(m: String): Int? = when (m) {
    "locked_order" -> R.string.mech_intro_locked_order; "nested" -> R.string.mech_intro_nested; "colour_gate" -> R.string.mech_intro_colour_gate
    "restricted_arc" -> R.string.mech_intro_restricted_arc; "rotation_chain" -> R.string.mech_intro_rotation_chain; "hinge_link" -> R.string.mech_intro_hinge_link
    "rotating_obstacle" -> R.string.mech_intro_rotating_obstacle; "double_gap" -> R.string.mech_intro_double_gap; else -> null
}

@Composable
fun chapterName(ch: ChapterDef): String {
    val lang = LocalConfiguration.current.locales[0].language
    return ch.name[lang] ?: ch.name["en"] ?: ""
}

/** Three illustrated worlds, five chapters each; locked chapters preview their mechanic. */
@Composable
fun WorldMapScreen(container: AppContainer, nav: Nav, world: Int) {
    val save by container.save.state.collectAsState()
    val w = container.content.world(world)
    val palette = LocalWorldPalette.current
    val worldUnlocked = world == 1 || save.chapterComplete((world - 1) * 5)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        SceneBackground(w.map, dim = 0.12f) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIconButton(contentDescription = stringResource(R.string.back), onClick = { nav.pop() }, sfx = "button_back") { Glyph("back", Modifier.size(26.dp)) }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        TitleText(stringResource(worldNameRes(world)), size = 24, color = DR.Cream)
                        Text("★ ${save.starsInWorld(world)} / 150", style = MaterialTheme.typography.labelLarge, color = DR.Gold)
                    }
                    CoinPill(save.coins)
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in 1..3) {
                        val unlocked = n == 1 || save.chapterComplete((n - 1) * 5)
                        WoodButton("$n", { if (unlocked) nav.replace(Screen.WorldMap(n)) }, Modifier.weight(1f), enabled = unlocked, color = if (n == world) palette.accent else DR.Wood, minHeight = 42.dp)
                    }
                }
                if (!worldUnlocked) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        WoodPanel { Text(stringResource(R.string.world_locked_text), style = MaterialTheme.typography.bodyLarge, color = DR.Ink, textAlign = TextAlign.Center) }
                    }
                    return@Column
                }
                val chapters = container.content.chapters.filter { it.world == world }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    chapters.forEachIndexed { idx, ch ->
                        val unlocked = ch.number == 1 || save.chapterComplete(ch.number - 1)
                        val done = save.chapterComplete(ch.number)
                        val stars = (ch.levels[0]..ch.levels[1]).sumOf { save.levels[LevelCodec.levelId(it)]?.stars ?: 0 }
                        ChapterCard(container, ch, unlocked, done, stars, Modifier.widthIn(max = 560.dp).fillMaxWidth().offset(x = if (landscape) 0.dp else (if (idx % 2 == 0) (-24).dp else 24.dp))) {
                            if (unlocked) nav.push(Screen.Chapter(ch.number))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ChapterCard(container: AppContainer, ch: ChapterDef, unlocked: Boolean, done: Boolean, stars: Int, modifier: Modifier, onClick: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(Brush.horizontalGradient(listOf(DR.Cream.copy(alpha = 0.96f), DR.CreamDark.copy(alpha = 0.92f))))
            .clickable(enabled = unlocked) { container.audio.sfx("button_tap"); onClick() }.padding(12.dp).alpha(if (unlocked) 1f else 0.75f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) {
            AssetImage(ch.scene, Modifier.fillMaxSize(), ContentScale.Crop)
            if (!unlocked) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) { Sprite("sheet15/node_lock_pulse_f01", Modifier.size(44.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chapter_n, ch.number) + " · " + chapterName(ch), style = MaterialTheme.typography.titleLarge, color = DR.WoodDeep)
            Text(stringResource(mechanicNameRes(ch.mechanic)), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
            if (!unlocked) Text(stringResource(R.string.next_mechanic, stringResource(mechanicNameRes(ch.mechanic))), style = MaterialTheme.typography.labelMedium, color = DR.SakuraDeep)
            else Row(verticalAlignment = Alignment.CenterVertically) { Glyph("star", Modifier.size(18.dp), DR.StarOn); Spacer(Modifier.width(4.dp)); Text("$stars / 30", style = MaterialTheme.typography.labelLarge, color = DR.WoodDeep) }
        }
        if (done) Sprite("sheet14/check_mark_f04", Modifier.size(36.dp))
        CharSprite(ch.rewardCharacter, "idle", Modifier.size(64.dp), alpha = if (done) 1f else 0.35f)
    }
}

/** Ten level nodes of a chapter, with stars and lock states from the world-map kit. */
@Composable
fun ChapterScreen(container: AppContainer, nav: Nav, chapter: Int) {
    val save by container.save.state.collectAsState()
    val ch = container.content.chapter(chapter)
    val world = container.content.world(ch.world)
    SceneBackground(world.map, dim = 0.3f) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.chapter_n, chapter) + " · " + chapterName(ch), { nav.pop() }, titleColor = DR.Cream) { CoinPill(save.coins) }
            mechanicIntroRes(ch.mechanic)?.let { intro ->
                Box(Modifier.padding(horizontal = 20.dp).widthIn(max = 560.dp)) { WoodPanel(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) { Text(stringResource(intro), style = MaterialTheme.typography.bodyMedium, color = DR.Ink) } }
            }
            LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items((ch.levels[0]..ch.levels[1]).toList()) { idx ->
                    val p = save.levels[LevelCodec.levelId(idx)]
                    val unlocked = save.isUnlocked(idx)
                    val node = when { !unlocked -> "sheet15/node_locked"; p?.completed != true -> "sheet15/node_unlocked"; else -> "sheet15/node_star${p.stars.coerceIn(1, 3)}" }
                    val chef = idx % 10 == 0
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = unlocked) { container.audio.sfx("button_tap"); nav.push(Screen.Play(idx)) }.padding(4.dp)
                        .semantics { contentDescription = "Level $idx" + (if (!unlocked) ", locked" else "") }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(contentAlignment = Alignment.Center) {
                            Sprite(node, Modifier.size(if (chef) 92.dp else 80.dp))
                            if (chef && unlocked) Sprite("sheet15/star_badge_gold_small", Modifier.size(28.dp).align(Alignment.TopEnd))
                        }
                        Text(idx.toString(), style = MaterialTheme.typography.labelLarge, color = DR.Cream)
                    }
                }
            }
        }
    }
}

/** Short illustrated chapter scene (skippable). */
@Composable
fun ChapterSceneScreen(container: AppContainer, chapter: Int, onDone: () -> Unit) {
    val ch = container.content.chapter(chapter)
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { container.audio.sfx("chapter_bell"); delay(600); ready = true }
    SceneBackground(ch.scene, dim = 0.15f) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            WoodPanel(Modifier.widthIn(max = 520.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TitleText(stringResource(R.string.chapter_complete), size = 26)
                    Text(stringResource(R.string.chapter_n, chapter) + " · " + chapterName(ch), style = MaterialTheme.typography.titleMedium, color = DR.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CharSprite(ch.rewardCharacter, "cheer", Modifier.size(96.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.unlocked_x, stringResource(cosmeticNameRes(container, characterCosmeticId(container, ch.rewardCharacter)))), style = MaterialTheme.typography.bodyLarge, color = DR.Ink)
                            Text(stringResource(R.string.unlocked_x, stringResource(cosmeticNameRes(container, ch.rewardCosmetic))), style = MaterialTheme.typography.bodyLarge, color = DR.Ink)
                            Text(stringResource(R.string.coins_earned, ch.rewardCoins), style = MaterialTheme.typography.labelLarge, color = DR.GoldDeep)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    WoodButton(stringResource(R.string.continue_), onDone, Modifier.fillMaxWidth(), color = DR.SakuraDeep, enabled = ready)
                }
            }
        }
    }
}

@Composable
fun WorldIntroScreen(container: AppContainer, world: Int, onDone: () -> Unit) {
    val w = container.content.world(world)
    LaunchedEffect(Unit) { container.audio.sfx("level_complete_bell") }
    SceneBackground(w.intro, dim = 0.1f) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            WoodPanel(Modifier.widthIn(max = 520.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TitleText(stringResource(R.string.world_unlocked), size = 26)
                    Text(stringResource(worldNameRes(world)), style = MaterialTheme.typography.headlineSmall, color = DR.WoodDeep)
                    Spacer(Modifier.height(12.dp))
                    CharSprite(w.character, "cheer", Modifier.size(110.dp))
                    Spacer(Modifier.height(12.dp))
                    WoodButton(stringResource(R.string.continue_), onDone, Modifier.fillMaxWidth(), color = DR.SakuraDeep)
                }
            }
        }
    }
}

/** Cosmetic id of the character sprite base (e.g. "sheet09/fox" -> "char_white_fox"). */
fun characterCosmeticId(container: AppContainer, rewardCharacter: String): String =
    container.content.cosmetics.firstOrNull { it.kind == "character" && it.sprite.startsWith("$rewardCharacter" + "_") }?.id ?: ("char_" + rewardCharacter.substringAfter('/'))

fun cosmeticNameRes(container: AppContainer, id: String): Int {
    val ctx = container.context
    val key = if (id.startsWith("char_") || id.startsWith("cos_")) id else "cos_$id"
    val res = ctx.resources.getIdentifier(key, "string", ctx.packageName)
    return if (res != 0) res else R.string.collection
}
