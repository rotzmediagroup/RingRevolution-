package com.shiostudios.dumplingrings.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shiostudios.dumplingrings.AppContainer
import com.shiostudios.dumplingrings.Nav
import com.shiostudios.dumplingrings.R
import com.shiostudios.dumplingrings.core.systems.Achievements
import com.shiostudios.dumplingrings.platform.CloudSaveService
import com.shiostudios.dumplingrings.ui.components.*
import com.shiostudios.dumplingrings.ui.theme.DR

private val achievementText = mapOf(
    "first_clear" to (R.string.ach_first_clear to R.string.ach_first_clear_desc),
    "first_chapter" to (R.string.ach_first_chapter to R.string.ach_first_chapter_desc),
    "blossom_master" to (R.string.ach_blossom_master to R.string.ach_blossom_master_desc),
    "lantern_master" to (R.string.ach_lantern_master to R.string.ach_lantern_master_desc),
    "mountain_master" to (R.string.ach_mountain_master to R.string.ach_mountain_master_desc),
    "star_garden" to (R.string.ach_star_garden to R.string.ach_star_garden_desc),
    "perfect_constellation" to (R.string.ach_perfect_constellation to R.string.ach_perfect_constellation_desc),
    "triple_release" to (R.string.ach_triple_release to R.string.ach_triple_release_desc),
    "daily_ritual" to (R.string.ach_daily_ritual to R.string.ach_daily_ritual_desc),
    "thousand_rings" to (R.string.ach_thousand_rings to R.string.ach_thousand_rings_desc),
    "teahouse_regular" to (R.string.ach_teahouse_regular to R.string.ach_teahouse_regular_desc),
    "halfway_home" to (R.string.ach_halfway_home to R.string.ach_halfway_home_desc),
    "century_club" to (R.string.ach_century_club to R.string.ach_century_club_desc),
    "mountain_keeper" to (R.string.ach_mountain_keeper to R.string.ach_mountain_keeper_desc),
    "chef_apprentice" to (R.string.ach_chef_apprentice to R.string.ach_chef_apprentice_desc),
    "chef_specialist" to (R.string.ach_chef_specialist to R.string.ach_chef_specialist_desc),
    "chef_grandmaster" to (R.string.ach_chef_grandmaster to R.string.ach_chef_grandmaster_desc),
    "star_seed" to (R.string.ach_star_seed to R.string.ach_star_seed_desc),
    "star_orchard" to (R.string.ach_star_orchard to R.string.ach_star_orchard_desc),
    "perfect_ten" to (R.string.ach_perfect_ten to R.string.ach_perfect_ten_desc),
    "perfect_fifty" to (R.string.ach_perfect_fifty to R.string.ach_perfect_fifty_desc),
    "first_daily" to (R.string.ach_first_daily to R.string.ach_first_daily_desc),
    "daily_devotee" to (R.string.ach_daily_devotee to R.string.ach_daily_devotee_desc),
    "hundred_rings" to (R.string.ach_hundred_rings to R.string.ach_hundred_rings_desc),
    "cascade_master" to (R.string.ach_cascade_master to R.string.ach_cascade_master_desc),
)

@Composable
fun GamesScreen(container: AppContainer, nav: Nav, activity: ComponentActivity) {
    val save by container.save.state.collectAsState()
    val games by container.games.state.collectAsState()
    val cloud by container.cloud.state.collectAsState()
    StudioBackdrop {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader(stringResource(R.string.games_title), { nav.pop() })
            LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    WoodPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.games_subtitle), style = MaterialTheme.typography.headlineSmall, color = DR.Ink)
                            Text(stringResource(if (!container.games.configured) R.string.games_unconfigured else if (games.signedIn) R.string.games_connected else R.string.games_offline), color = DR.InkSoft)
                            if (container.games.configured) WoodButton(stringResource(if (games.signedIn) R.string.games_achievements else R.string.games_sign_in), {
                                if (games.signedIn) container.games.showAchievements(activity) else container.games.attach(activity, true)
                            }, Modifier.fillMaxWidth(), enabled = !games.busy, color = DR.Indigo)
                            if (games.failed) Text(stringResource(R.string.games_error), color = DR.Red)
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.leaderboards), style = MaterialTheme.typography.headlineSmall, color = DR.Ink)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.leaderboards_description), color = DR.InkSoft)
                    Spacer(Modifier.height(8.dp))
                    WoodButton(stringResource(R.string.leaderboards), { container.games.showLeaderboards(activity) }, Modifier.fillMaxWidth(), enabled = games.signedIn, color = DR.Bamboo)
                }
                item {
                    WoodPanel(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                MedalArt(11, Modifier.size(72.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.cloud_title), style = MaterialTheme.typography.titleLarge, color = DR.Ink)
                                    Text(stringResource(R.string.cloud_description), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                                }
                            }
                            WoodButton(stringResource(R.string.cloud_backup), { container.cloud.open(activity, true) }, Modifier.fillMaxWidth(), enabled = games.signedIn && !cloud.busy && cloud.choice == null)
                            WoodButton(stringResource(R.string.cloud_restore), { container.cloud.open(activity, false) }, Modifier.fillMaxWidth(), enabled = games.signedIn && !cloud.busy && cloud.choice == null, color = DR.Indigo)
                            if (cloud.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DR.Bamboo)
                            cloud.message?.let {
                                Text(stringResource(when (it) {
                                    CloudSaveService.Result.SAVED -> R.string.cloud_saved
                                    CloudSaveService.Result.RESTORED -> R.string.cloud_restored
                                    CloudSaveService.Result.NEED_SIGN_IN -> R.string.games_offline
                                    CloudSaveService.Result.FAILED -> R.string.cloud_failed
                                }), color = DR.InkSoft)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.achievement_progress, Achievements.all.count { it.key in save.achievements }, Achievements.all.size), style = MaterialTheme.typography.headlineSmall, color = DR.Ink)
                }
                items(Achievements.all, key = { it.key }) { achievement ->
                    val unlocked = achievement.key in save.achievements
                    val current = achievement.progress(save).coerceIn(0, achievement.target)
                    val (nameRes, descriptionRes) = achievementText.getValue(achievement.key)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (unlocked) DR.Gold.copy(alpha = .24f) else DR.CreamDark.copy(alpha = .6f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MedalArt(achievement.art, Modifier.size(82.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(nameRes), style = MaterialTheme.typography.titleLarge, color = DR.Ink)
                            Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium, color = DR.InkSoft)
                            LinearProgressIndicator(progress = { current.toFloat() / achievement.target }, modifier = Modifier.fillMaxWidth(), color = DR.Bamboo, trackColor = DR.Wood.copy(alpha = .15f))
                            Text(if (unlocked) stringResource(R.string.achievement_unlocked) else stringResource(R.string.achievement_progress, current, achievement.target), style = MaterialTheme.typography.labelMedium, color = DR.InkSoft)
                        }
                    }
                }
            }
        }
    }
    cloud.choice?.let { choice ->
        AlertDialog(onDismissRequest = { container.cloud.cancel() },
            title = { Text(stringResource(if (choice.conflict) R.string.cloud_conflict else R.string.cloud_choose)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(if (choice.upload && !choice.conflict) R.string.cloud_upload_warning else R.string.cloud_replace_warning))
                Text(stringResource(if (choice.conflict) R.string.cloud_copy_b else R.string.cloud_device, choice.local.totalStars, choice.local.levels.count { it.value.completed }))
                Text(stringResource(if (choice.conflict) R.string.cloud_copy_a else R.string.cloud_remote, choice.remote.totalStars, choice.remote.levels.count { it.value.completed }))
                Text(stringResource(R.string.cloud_saved_at, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(choice.savedAt))), style = MaterialTheme.typography.bodySmall)
            } },
            confirmButton = { TextButton(onClick = { container.cloud.choose(true) }) { Text(stringResource(if (choice.conflict) R.string.cloud_use_a else if (choice.upload) R.string.cloud_upload_cancel else R.string.cloud_use_remote)) } },
            dismissButton = { TextButton(onClick = { container.cloud.choose(false) }) { Text(stringResource(if (choice.conflict) R.string.cloud_use_b else if (choice.upload) R.string.cloud_upload_confirm else R.string.cloud_keep_device)) } })
    }
}
