@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.omni.dj.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.omni.dj.R
import com.omni.dj.core.*
import com.omni.dj.data.*
import com.omni.dj.playback.*
import kotlin.math.*

@UnstableApi
@Composable
fun DjApp(model: DjViewModel) = OmniTheme {
    val library by model.library.collectAsStateWithLifecycle()
    val songs by model.visibleSongs.collectAsStateWithLifecycle()
    val state by model.playback.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val analyses by model.analyses.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val analysisProgress by model.analysisProgress.collectAsStateWithLifecycle()
    val query by model.search.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val sort by model.sort.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var expansion by rememberSaveable { mutableFloatStateOf(0f) }
    val fraction by animateFloatAsState(expansion, spring(dampingRatio = .9f, stiffness = 420f), label = "player expansion")
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    val notification = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    BackHandler(expansion > 0f && sheet == null) { expansion = 0f }
    val error = when (state.issue) {
        PlaybackIssue.FILE_UNAVAILABLE -> stringResource(R.string.file_unavailable)
        PlaybackIssue.NEXT_UNAVAILABLE -> stringResource(R.string.next_unavailable)
        PlaybackIssue.NONE -> ""
    }
    LaunchedEffect(error) { if (error.isNotBlank()) { snackbar.showSnackbar(error); model.clearError() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val screenHeight = maxHeight
        val screenWidth = maxWidth
        val density = LocalDensity.current
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = if (state.current == null) 30.dp else 112.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.app_name), fontSize = 29.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.library_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    IconButton(onClick = { sheet = "settings" }) { Icon(Icons.Rounded.Tune, stringResource(R.string.settings)) }
                }
                Spacer(Modifier.height(24.dp))
                Surface(shape = RoundedCornerShape(26.dp), color = Panel) {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Violet.copy(alpha = .13f), Panel))).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = Violet, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.your_personal_dj), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(settings.autoMix, { model.updateSettings { s -> s.copy(autoMix = it) } })
                        }
                        Text(stringResource(if (settings.autoMix) R.string.auto_description else R.string.auto_disabled_description),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(moodString(settings.mood)), color = Mint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            FilledTonalButton(enabled = songs.isNotEmpty(), onClick = {
                                songs.firstOrNull()?.let { model.play(it) }
                                if (Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                                    notification.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }) { Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.start_session)) }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(value = query, onValueChange = { model.search.value = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(20.dp), placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotEmpty()) IconButton({ model.search.value = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_search)) } })
                Spacer(Modifier.height(10.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryFilter.entries.forEach { tab -> FilterChip(filter == tab, { model.filter.value = tab }, { Text(stringResource(filterString(tab))) }) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.song_count, songs.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(menu, { menu = false }) { LibrarySort.entries.forEach { choice ->
                            DropdownMenuItem(text = { Text(stringResource(sortString(choice))) }, onClick = { model.sort.value = choice; menu = false },
                                trailingIcon = { if (sort == choice) Icon(Icons.Rounded.Check, null) })
                        } }
                    }
                    IconButton({ model.refresh() }) { Icon(Icons.Rounded.Refresh, stringResource(R.string.refresh)) }
                }
                if (analysisProgress.total > 0) {
                    val label = when {
                        analysisProgress.running && state.playing -> stringResource(R.string.analysis_paused)
                        analysisProgress.running -> stringResource(R.string.analysis_progress, analysisProgress.done, analysisProgress.total)
                        analysisProgress.failed > 0 -> stringResource(R.string.analysis_partial, analysisProgress.failed)
                        else -> stringResource(R.string.analysis_ready)
                    }
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (analysisProgress.running) LinearProgressIndicator(progress = { analysisProgress.done.toFloat() / analysisProgress.total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp), color = Violet)
                }
            }
            when {
                !library.permission -> item { EmptyState(Icons.Rounded.LibraryMusic, R.string.permission_title, R.string.permission_body) {
                    Button({ permission.launch(MusicLibrary.permission) }) { Text(stringResource(R.string.allow_music)) }
                } }
                library.loading && songs.isEmpty() -> item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                library.failed -> item { EmptyState(Icons.Rounded.Refresh, R.string.library_error_title, R.string.library_error_body) { Button({ model.refresh() }) { Text(stringResource(R.string.retry)) } } }
                songs.isEmpty() -> item { EmptyState(Icons.Rounded.MusicNote, R.string.no_music_title, R.string.no_music_body) }
                filter == LibraryFilter.ALBUMS -> {
                    songs.groupBy { it.albumId }.forEach { (_, albumSongs) ->
                        item(key = "album-${albumSongs.first().albumId}") {
                            Text(albumSongs.first().album.ifBlank { stringResource(R.string.unknown_album) }, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp), fontWeight = FontWeight.Bold)
                        }
                        items(albumSongs, key = { it.id }) { song -> SongRow(song, song.id == state.current?.id, song.id in favorites,
                            analyses[song.id], { model.play(song) }, { model.favorite(song) }, { model.playNext(song) }, Modifier.animateItem()) }
                    }
                }
                else -> items(songs, key = { it.id }) { song -> SongRow(song, song.id == state.current?.id, song.id in favorites,
                    analyses[song.id], { model.play(song) }, { model.favorite(song) }, { model.playNext(song) }, Modifier.animateItem()) }
            }
        }
        state.current?.let { song ->
            val artSize = 48.dp + (minOf(screenWidth - 72.dp, screenHeight * .31f, 310.dp) - 48.dp) * fraction
            val panelHeight = 78.dp + (screenHeight - 78.dp) * fraction
            val artX = 14.dp + ((screenWidth - artSize) / 2 - 14.dp) * fraction
            val artY = 15.dp + 43.dp * fraction
            val drag = Modifier.draggable(rememberDraggableState { delta ->
                val heightPx = with(density) { (screenHeight - 78.dp).toPx() }
                expansion = (expansion - delta / heightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }, Orientation.Vertical, onDragStopped = { velocity -> expansion = if (abs(velocity) > 650) { if (velocity < 0) 1f else 0f } else if (expansion > .45f) 1f else 0f })
            Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(panelHeight),
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp), color = Panel, shadowElevation = 18.dp) {
                Box(Modifier.fillMaxSize()) {
                    if (fraction > .05f) {
                        Column(Modifier.fillMaxWidth().alpha(fraction)) {
                            Row(Modifier.fillMaxWidth().then(drag).height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ expansion = 0f }) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.collapse_player)) }
                                Text(stringResource(R.string.now_playing), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton({ model.favorite(song) }) { Icon(if (song.id in favorites) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, stringResource(R.string.favorite), tint = if (song.id in favorites) Violet else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            ExpandedPlayer(song, state, settings, analyses[song.id], artSize + 34.dp,
                                model, { sheet = it })
                        }
                    }
                    Artwork(song, Modifier.offset(x = artX, y = artY).size(artSize), corner = 12.dp + 18.dp * fraction)
                    if (fraction < .95f) {
                        Row(Modifier.fillMaxWidth().height(76.dp).alpha((1 - fraction * 4).coerceIn(0f, 1f)).then(drag).clickable { expansion = 1f }
                            .padding(start = 76.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                Text(if (state.mixing) stringResource(R.string.mixing_now) else artist(song), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = if (state.mixing) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ model.engine?.toggle() }) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (state.playing) R.string.pause else R.string.play)) }
                            IconButton({ model.engine?.next() }) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next)) }
                        }
                    }
                    if (fraction < .9f) LinearProgressIndicator(progress = { state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).alpha(1 - fraction), color = Violet)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.current != null) 88.dp else 12.dp))
    }
    when (sheet) {
        "settings" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel) { SettingsPanel(settings, model) }
        "pro" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) { ProPanel(state, analyses[state.current?.id], model) { sheet = "grid" } }
        "grid" -> state.current?.let { song -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel) { BeatGridPanel(song, analyses[song.id], model) { sheet = "pro" } } }
        "queue" -> ModalBottomSheet(onDismissRequest = { sheet = null }, containerColor = Panel) { QueuePanel(state, model) }
    }
}

@Composable
fun Artwork(song: Song?, modifier: Modifier = Modifier, corner: Dp = 18.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(Color(0xFF4B4777), Color(0xFF27233E)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.GraphicEq, null, Modifier.fillMaxSize(.45f), tint = Violet.copy(alpha = .8f))
        AsyncImage(song?.artwork, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    }
}

@Composable
private fun SongRow(song: Song, active: Boolean, favorite: Boolean, analysis: TrackAnalysis?, play: () -> Unit, star: () -> Unit, next: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) Violet.copy(alpha = .10f) else Color.Transparent)
        .clickable(onClick = play).padding(vertical = 8.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song, Modifier.size(52.dp), 13.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Violet else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(artist(song), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time(song.durationMs), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (analysis?.hasReliableBeat == true) Text(stringResource(R.string.bpm_value, analysis.bpm.roundToInt()), fontSize = 10.sp, color = Violet)
        }
        Box {
            IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.song_options)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text(stringResource(R.string.play_next)) }, { next(); menu = false }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) })
                DropdownMenuItem({ Text(stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite)) }, { star(); menu = false }, leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, null) })
            }
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: Int, body: Int, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = Violet)
        Text(stringResource(title), fontWeight = FontWeight.Bold)
        Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}

@UnstableApi
@Composable
private fun ExpandedPlayer(song: Song, state: PlaybackState, settings: MixSettings, analysis: TrackAnalysis?, topSpace: Dp, model: DjViewModel, open: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 26.dp, end = 26.dp, top = topSpace, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(song.title, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(artist(song), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            var scrub by remember(song.id) { mutableStateOf<Float?>(null) }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Waveform(analysis?.waveform.orEmpty(), state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1))
                Slider(value = scrub ?: state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat()), onValueChange = { scrub = it },
                    onValueChangeFinished = { scrub?.let { model.engine?.seek(it.toLong()) }; scrub = null }, valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat())
                Row { Text(time(scrub?.toLong() ?: state.positionMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); Text(time(state.durationMs), style = MaterialTheme.typography.labelSmall) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton({ open("queue") }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.queue)) }
                IconButton({ model.engine?.previous() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.previous), Modifier.size(30.dp)) }
                FilledIconButton({ model.engine?.toggle() }, Modifier.size(72.dp), shape = CircleShape) { Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (state.playing) R.string.pause else R.string.play), Modifier.size(38.dp)) }
                IconButton({ model.engine?.next() }, Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext, stringResource(R.string.next), Modifier.size(30.dp)) }
                IconButton({ open("pro") }) { Icon(Icons.Rounded.Tune, stringResource(R.string.pro_controls)) }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(22.dp), color = Background.copy(alpha = .65f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.GraphicEq, null, tint = if (settings.autoMix) Mint else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.mixing) R.string.mixing_now else if (settings.autoMix) R.string.auto_mix_on else R.string.auto_mix_off), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (analysis?.hasReliableBeat == true) Text(stringResource(R.string.bpm_value, analysis.bpm.roundToInt()), fontSize = 12.sp, color = Violet)
                    }
                    state.next?.let { next ->
                        Text(stringResource(R.string.next_song, next.title), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.mixing) {
                        Text(stringResource(styleString(state.plan?.style ?: TransitionStyle.SMOOTH)), color = Mint, style = MaterialTheme.typography.labelSmall)
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = Mint)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilledTonalButton({ model.engine?.mixNow() }, enabled = state.next != null && state.playing, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.mix_now)) }
                            OutlinedButton({ open("settings") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.mix_settings)) }
                        }
                    }
                }
            }
        }
        state.lastMix?.let { mix -> item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.rate_mix), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(if (mix.feedback != null) R.string.mix_feedback_saved else R.string.mix_feedback_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton({ model.engine?.rateLastMix(true) }) { Icon(Icons.Rounded.ThumbUp, stringResource(R.string.like_mix), tint = if (mix.feedback == true) Mint else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton({ model.engine?.rateLastMix(false) }) { Icon(Icons.Rounded.ThumbDown, stringResource(R.string.dislike_mix), tint = if (mix.feedback == false) Violet else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } }
    }
}

@Composable
fun Waveform(samples: List<Float>, progress: Float) {
    val violet = Violet
    Canvas(Modifier.fillMaxWidth().height(38.dp)) {
        if (samples.isEmpty()) {
            drawLine(violet.copy(alpha = .2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
        } else {
            val max = samples.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
            val width = size.width / samples.size
            samples.forEachIndexed { i, amplitude ->
                val height = (amplitude / max).coerceIn(.06f, 1f) * size.height
                drawLine(if (i.toFloat() / samples.size <= progress) violet else violet.copy(alpha = .25f),
                    Offset(i * width + width / 2, (size.height - height) / 2), Offset(i * width + width / 2, (size.height + height) / 2), (width * .55f).coerceAtLeast(1f))
            }
        }
    }
}

@Composable fun artist(song: Song): String = if (song.artist.isBlank() || song.artist == "<unknown>") stringResource(R.string.unknown_artist) else song.artist
fun time(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%d:%02d".format(seconds / 60, seconds % 60) }
fun styleString(style: TransitionStyle): Int = when (style) {
    TransitionStyle.AUTO -> R.string.style_auto; TransitionStyle.SMOOTH -> R.string.style_smooth
    TransitionStyle.BEAT_BLEND -> R.string.style_beat; TransitionStyle.BASS_SWAP -> R.string.style_bass
    TransitionStyle.FILTER_SWEEP -> R.string.style_filter; TransitionStyle.ECHO_OUT -> R.string.style_echo
    TransitionStyle.QUICK_CUT -> R.string.style_cut
}
fun moodString(mood: SessionMood): Int = when (mood) { SessionMood.BALANCED -> R.string.mood_balanced; SessionMood.PARTY -> R.string.mood_party; SessionMood.WORKOUT -> R.string.mood_workout; SessionMood.CHILL -> R.string.mood_chill }
fun filterString(filter: LibraryFilter): Int = when (filter) { LibraryFilter.ALL -> R.string.all_songs; LibraryFilter.FAVORITES -> R.string.favorites; LibraryFilter.ALBUMS -> R.string.albums }
fun sortString(sort: LibrarySort): Int = when (sort) { LibrarySort.TITLE -> R.string.sort_title; LibrarySort.ARTIST -> R.string.sort_artist; LibrarySort.RECENT -> R.string.sort_recent }
