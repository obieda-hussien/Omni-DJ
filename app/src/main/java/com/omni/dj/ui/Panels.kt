@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.omni.dj.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.media3.common.util.UnstableApi
import com.omni.dj.R
import com.omni.dj.core.*
import com.omni.dj.playback.PlaybackState
import com.omni.dj.data.Song
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun SettingsPanel(settings: MixSettings, model: DjViewModel) {
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 42.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.mix_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { SettingSwitch(R.string.auto_mix, R.string.auto_description, settings.autoMix) { value -> model.updateSettings { it.copy(autoMix = value) } } }
        item { SettingSwitch(R.string.smart_order, R.string.smart_order_body, settings.smartOrder) { value -> model.updateSettings { it.copy(smartOrder = value) } } }
        item {
            Text(stringResource(R.string.session_mood), fontWeight = FontWeight.Medium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SessionMood.entries.forEach { mood -> FilterChip(settings.mood == mood, { model.updateSettings { it.copy(mood = mood) } }, { Text(stringResource(moodString(mood))) }) }
            }
        }
        item {
            var expanded by remember { mutableStateOf(false) }
            Text(stringResource(R.string.transition_style), fontWeight = FontWeight.Medium)
            Box {
                OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) {
                    Text(stringResource(styleString(settings.style)), Modifier.weight(1f)); Icon(Icons.Rounded.KeyboardArrowDown, null)
                }
                DropdownMenu(expanded, { expanded = false }) { TransitionStyle.entries.forEach { style ->
                    DropdownMenuItem({ Text(stringResource(styleString(style))) }, { model.updateSettings { it.copy(style = style) }; expanded = false },
                        trailingIcon = { if (style == settings.style) Icon(Icons.Rounded.Check, null) })
                } }
            }
            Text(stringResource(R.string.style_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text(stringResource(R.string.transition_length, settings.transitionSeconds), fontWeight = FontWeight.Medium)
            Slider(settings.transitionSeconds.toFloat(), { value -> model.updateSettings { it.copy(transitionSeconds = value.roundToInt()) } }, valueRange = 2f..16f, steps = 13)
        }
        item { SettingSwitch(R.string.normalize, R.string.normalize_body, settings.normalize) { value -> model.updateSettings { it.copy(normalize = value) } } }
        item { SettingSwitch(R.string.short_overlap, R.string.short_overlap_body, settings.preserveVocals) { value -> model.updateSettings { it.copy(preserveVocals = value) } } }
        item {
            Text(stringResource(R.string.tempo_limit, settings.maxTempoPercent), fontWeight = FontWeight.Medium)
            Slider(settings.maxTempoPercent.toFloat(), { value -> model.updateSettings { it.copy(maxTempoPercent = value.roundToInt()) } }, valueRange = 0f..10f, steps = 9)
            Text(stringResource(R.string.tempo_limit_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        item {
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.analysis_title), fontWeight = FontWeight.Medium)
            Text(stringResource(R.string.analysis_body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            TextButton({ model.refresh(forceAnalysis = true) }) { Text(stringResource(R.string.resume_analysis)) }
        }
    }
}

@Composable
private fun SettingSwitch(title: Int, body: Int, value: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(stringResource(title), fontWeight = FontWeight.Medium)
            Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, change)
    }
}

@UnstableApi
@Composable
fun ProPanel(state: PlaybackState, analysis: TrackAnalysis?, model: DjViewModel, editGrid: () -> Unit) {
    val effects = state.effects
    fun update(e: com.omni.dj.playback.DeckEffects) { model.engine?.setEffects(e.low, e.mid, e.high, e.filter, e.echo) }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(stringResource(R.string.pro_controls), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pro_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeckCard(state.current?.title, R.string.deck_a, Modifier.weight(1f))
                    DeckCard(state.next?.title, R.string.deck_b, Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.crossfader), modifier = Modifier.padding(top = 14.dp), fontWeight = FontWeight.Medium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(state.manualCrossfade ?: state.progress, { model.engine?.setCrossfade(it) }, enabled = state.next != null, valueRange = 0f..1f)
            }
            Text(stringResource(R.string.crossfader_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.speed_percent, (state.speed * 100).roundToInt()), fontWeight = FontWeight.Medium)
            Slider(state.speed, { model.engine?.setSpeed(it) }, valueRange = .9f..1.1f, enabled = state.current != null)
        }
        item {
            Text(stringResource(R.string.eq), fontWeight = FontWeight.Bold)
            ControlSlider(R.string.eq_low, effects.low, 0f..1.5f) { update(effects.copy(low = it)) }
            ControlSlider(R.string.eq_mid, effects.mid, 0f..1.5f) { update(effects.copy(mid = it)) }
            ControlSlider(R.string.eq_high, effects.high, 0f..1.5f) { update(effects.copy(high = it)) }
            ControlSlider(R.string.filter, effects.filter, 0f..1f) { update(effects.copy(filter = it)) }
            ControlSlider(R.string.echo, effects.echo, 0f..1f) { update(effects.copy(echo = it)) }
        }
        item {
            Text(stringResource(R.string.loop), fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 4, 8, 16).forEach { beats ->
                    FilterChip(state.loopBeats == beats, { model.engine?.setLoop(beats) }, enabled = beats == 0 || analysis?.hasReliableBeat == true,
                        label = { Text(if (beats == 0) stringResource(R.string.loop_off) else stringResource(R.string.loop_beats, beats)) })
                }
            }
            Text(stringResource(R.string.loop_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(editGrid, enabled = state.current != null) { Text(stringResource(R.string.edit_grid)) }
        }
        item {
            OutlinedButton({ model.engine?.resetControls() }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.reset_controls))
            }
        }
    }
}

@UnstableApi
@Composable
fun BeatGridPanel(song: Song, analysis: TrackAnalysis?, model: DjViewModel, done: () -> Unit) {
    var bpm by remember(song.id) { mutableFloatStateOf((analysis?.bpm ?: 120f).takeIf { it in 65f..180f } ?: 120f) }
    var offset by remember(song.id) { mutableFloatStateOf((analysis?.beatOffsetMs ?: 0).toFloat()) }
    val tap = remember(song.id) { TapTempo() }
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(stringResource(R.string.edit_grid), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.grid_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Text(stringResource(R.string.bpm_value, bpm.roundToInt()), fontWeight = FontWeight.Bold)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(bpm, { bpm = it; offset = offset.coerceAtMost(60000 / bpm - 1) }, valueRange = 65f..180f, steps = 114)
            }
            OutlinedButton({ tap.tap(android.os.SystemClock.elapsedRealtime())?.let { bpm = it; offset = offset.coerceAtMost(60000 / bpm - 1) } }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.TouchApp, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.tap_tempo))
            }
        }
        item {
            val period = 60000 / bpm
            Text(stringResource(R.string.beat_offset, offset.roundToInt()), fontWeight = FontWeight.Medium)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Slider(offset.coerceIn(0f, period - 1), { offset = it }, valueRange = 0f..(period - 1))
            }
            Text(stringResource(R.string.beat_offset_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Button({ model.correctGrid(song, bpm, offset.toLong()); done() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_grid)) } }
    }
}

@Composable
private fun DeckCard(title: String?, label: Int, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Background) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(label), color = Violet, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Text(title ?: stringResource(R.string.no_next_song), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ControlSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall)
        Slider(value, change, Modifier.weight(1f), valueRange = range)
    }
}

@UnstableApi
@Composable
fun QueuePanel(state: PlaybackState, model: DjViewModel) {
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(stringResource(R.string.queue), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.queue_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.queue.isEmpty()) item { Text(stringResource(R.string.no_next_song), modifier = Modifier.padding(vertical = 30.dp)) }
        items(state.queue, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth().animateItem().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(song, Modifier.size(44.dp), 12.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(artist(song), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ model.playNext(song) }) { Icon(Icons.Rounded.VerticalAlignTop, stringResource(R.string.play_next)) }
                IconButton({ model.engine?.removeQueued(song.id) }) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_from_queue)) }
            }
        }
    }
}
