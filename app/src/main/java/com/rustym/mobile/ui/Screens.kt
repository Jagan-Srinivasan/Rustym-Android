package com.rustym.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.rustym.mobile.data.Track
import com.rustym.mobile.data.Yt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

// ---------------------------------------------------------------- Home

@Composable
fun HomeScreen(vm: PlayerVM, onLogin: () -> Unit) {
    var trending by remember { mutableStateOf<List<Track>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        failed = false
        trending = null
        trending = withContext(Dispatchers.IO) {
            runCatching { Yt.search("top songs this week") }.getOrNull()
        }
        if (trending == null) failed = true
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val first = vm.account?.name?.takeIf { it != "Signed in" }?.substringBefore(' ')
                    Text(if (first != null) "Hi, $first" else "Rustym", style = MaterialTheme.typography.headlineMedium, color = Accent)
                    Text("No ads. Just music.", color = Dim)
                }
                AccountButton(vm, onLogin)
            }
        }
        if (vm.recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            val list = vm.recent.take(8)
            itemsIndexed(list, key = { _, t -> "r" + t.id }) { i, t ->
                TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) })
            }
        }
        item { SectionTitle("Trending") }
        val list = trending
        when {
            failed -> item {
                Column(Modifier.padding(16.dp)) {
                    Text("Couldn't load trending songs. Check your connection.", color = Dim)
                    TextButton(onClick = { reload++ }) { Text("Retry") }
                }
            }
            list == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Accent) } }
            else -> itemsIndexed(list, key = { _, t -> "t" + t.id }) { i, t ->
                TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
            }
        }
    }
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen(vm: PlayerVM) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus: FocusManager = LocalFocusManager.current

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        focus.clearFocus()
        loading = true
        error = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Yt.search(q) } }
            loading = false
            r.onSuccess { results = it; vm.prefetchTracks(it.take(7)) }.onFailure { error = "Search failed: ${it.message ?: "network error"}" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            placeholder = { Text("Search songs, artists…") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null }) { Icon(Icons.Filled.Clear, "Clear") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        error?.let { EmptyNote(it) }
        val list = results
        when {
            list == null && !loading && error == null -> EmptyNote("Search for a song to get started.")
            list != null && list.isEmpty() -> EmptyNote("Nothing found.")
            list != null -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { i, t -> "$i-${t.id}" }) { i, t ->
                    TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Account

@Composable
fun AccountButton(vm: PlayerVM, onLogin: () -> Unit) {
    val acc = vm.account
    if (acc == null) {
        TextButton(onClick = onLogin) { Text("Sign in", color = Accent) }
    } else {
        Art(acc.photo, 40.dp, corner = 20.dp)
    }
}

// ---------------------------------------------------------------- Library

private enum class LibTab(val label: String, val icon: ImageVector) {
    Favourites("Liked songs", Icons.Filled.Favorite),
    Recent("Recent", Icons.Filled.History),
    Playlists("Playlists", Icons.Filled.QueueMusic),
    Stats("Stats", Icons.Filled.BarChart),
}

/** A tappable card for one Library section. The selected card is tinted with the accent colour. */
@Composable
private fun LibTabCard(label: String, icon: ImageVector, sub: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(124.dp)
            .clip(shape)
            .background(if (selected) Accent.copy(alpha = 0.14f) else Surface1)
            .border(1.5.dp, if (selected) Accent else Surface2, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(if (selected) Accent else Surface2, CircleShape),
            Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (selected) Color.White else Dim)
        }
        Text(
            label, Modifier.padding(top = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
        )
        Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun LibraryScreen(vm: PlayerVM) {
    var tab by remember { mutableStateOf(LibTab.Favourites) }

    LaunchedEffect(tab, vm.account) {
        if (tab == LibTab.Favourites) vm.loadLiked()
        if (tab == LibTab.Playlists) vm.loadPlaylists()
    }
    // leaving the Playlists tab closes an opened playlist
    LaunchedEffect(tab) { if (tab != LibTab.Playlists) vm.closePlaylist() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LibTab.entries.forEach {
                val sub = when (it) {
                    LibTab.Favourites -> "${vm.favorites.size} songs"
                    LibTab.Recent -> "${vm.recent.size} songs"
                    LibTab.Playlists -> vm.playlists?.let { l -> "${l.size} playlists" } ?: "YouTube Music"
                    LibTab.Stats -> "Your listening"
                }
                LibTabCard(it.label, it.icon, sub, selected = tab == it) { tab = it }
            }
        }

        when (tab) {
            LibTab.Favourites -> YtState(vm, vm.liked, retry = { vm.loadLiked(force = true) }) { _ ->
                TrackList(vm, vm.favorites, "Songs you like show up here. Sign in from Settings to sync YouTube Music likes.")
            }
            LibTab.Recent -> TrackList(vm, vm.recent, "Songs you play show up here.", clearable = true)
            LibTab.Stats -> StatsPanel(vm)
            LibTab.Playlists -> when {
                vm.account == null -> EmptyNote("Sign in with Google in Settings to browse your YouTube Music playlists.")
                vm.openedPlaylist != null -> {
                    val p = vm.openedPlaylist!!
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                        IconButton(onClick = vm::closePlaylist) { Icon(Icons.Filled.KeyboardArrowDown, "Back", Modifier.rotate(90f)) }
                        Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    }
                    YtState(vm, vm.openedTracks, retry = { vm.openPlaylist(p) }) { list ->
                        TrackList(vm, list, "This playlist is empty.")
                    }
                }
                else -> YtState(vm, vm.playlists, retry = { vm.loadPlaylists(force = true) }) { list ->
                    if (list.isEmpty()) EmptyNote("No playlists found in your library.")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.browseId }) { c ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp)).background(Surface1)
                                    .clickable { vm.openPlaylist(c) }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Art(c.thumb, 52.dp)
                                Column {
                                    Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.sub, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(vm: PlayerVM) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                    Text("Listening time", color = Dim, style = MaterialTheme.typography.bodySmall)
                    val minutes = vm.totalListeningMs / 60_000L
                    Text(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m", style = MaterialTheme.typography.headlineSmall, color = Accent)
                }
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Surface1).padding(16.dp)) {
                    Text("Tracked songs", color = Dim, style = MaterialTheme.typography.bodySmall)
                    Text("${vm.trackedSongCount}", style = MaterialTheme.typography.headlineSmall, color = Accent)
                }
            }
            SectionTitle("Most played", Modifier.padding(start = 0.dp, top = 12.dp))
        }
        if (vm.topPlayed.isEmpty()) item { EmptyNote("Play some music to build your listening stats.") }
        itemsIndexed(vm.topPlayed, key = { _, item -> item.first.id }) { i, item ->
            val (track, count) = item
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}", color = Dim, modifier = Modifier.padding(end = 12.dp))
                Art(track.thumb, 44.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                Text("$count plays", color = Accent, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Shows loading / error / retry around data fetched from the signed-in account. */
@Composable
private fun <T> YtState(vm: PlayerVM, data: T?, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when {
        data != null -> content(data)
        vm.ytError != null -> Column(Modifier.padding(16.dp)) {
            Text(vm.ytError.orEmpty(), color = Dim)
            TextButton(onClick = retry) { Text("Retry", color = Accent) }
        }
        else -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Accent) }
    }
}

@Composable
private fun TrackList(vm: PlayerVM, list: List<Track>, emptyText: String, clearable: Boolean = false) {
    if (list.isEmpty()) { EmptyNote(emptyText); return }
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { vm.play(list, 0) }) { Text("Play all", color = Accent) }
        TextButton(onClick = { vm.play(list.shuffled(), 0) }) { Text("Shuffle", color = Accent) }
        if (clearable) TextButton(onClick = { vm.clearRecent() }) { Text("Clear", color = Dim) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(list, key = { i, t -> "$i-${t.id}" }) { i, t ->
            TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
        }
    }
}

// ---------------------------------------------------------------- Mini player

@Composable
fun MiniPlayer(vm: PlayerVM, onOpen: () -> Unit) {
    val t = vm.current ?: return
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(1L) }
    LaunchedEffect(t.id, vm.isPlaying) {
        while (true) {
            pos = vm.positionMs(); dur = vm.durationMs().coerceAtLeast(1L)
            delay(500)
        }
    }
    Column(
        Modifier.fillMaxWidth()
            .background(Surface2.copy(alpha = if (ThemeStore.glassEffects) 0.78f else 1f))
            .pointerInput(vm) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = { if (drag < -48f) vm.next() else if (drag > 48f) vm.previous(); drag = 0f },
                    onDragCancel = { drag = 0f },
                )
            }
            .clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.thumb, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
            else IconButton(onClick = vm::togglePlay) {
                Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = vm::next) { Icon(Icons.Filled.SkipNext, "Next") }
        }
        LinearProgressIndicator(progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Surface1)
    }
}

// ---------------------------------------------------------------- Now playing

@Composable
private fun LyricsGapDots() {
    val transition = rememberInfiniteTransition(label = "lyrics-gap")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = index * 170), repeatMode = RepeatMode.Reverse),
                label = "dot-$index",
            )
            Box(Modifier.size(7.dp).background(Accent.copy(alpha = alpha), CircleShape))
        }
    }
}

@Composable
fun NowPlayingScreen(vm: PlayerVM, onClose: () -> Unit) {
    val t = vm.current
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(1L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }

    LaunchedEffect(t?.id, vm.isPlaying) {
        while (true) {
            if (!dragging) { pos = vm.positionMs(); dur = vm.durationMs().coerceAtLeast(1L) }
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        if (t != null && t.thumb.isNotBlank()) {
            coil.compose.AsyncImage(model = t.thumb, contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(80.dp).background(Bg.copy(alpha = 0.55f)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.22f)
        }
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground) }
            Text(if (showQueue) "Up next" else "Now playing", Modifier.weight(1f), textAlign = TextAlign.Center, color = Dim)
            TextButton(onClick = {
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                val nextSpeed = speeds[(speeds.indexOf(vm.playbackSpeed).coerceAtLeast(0) + 1) % speeds.size]
                vm.updatePlaybackSpeed(nextSpeed)
            }) { Text("${vm.playbackSpeed}x", color = Accent) }
            TextButton(onClick = { showQueue = !showQueue }) { Text(if (showQueue) "Player" else "Queue", color = Accent) }
        }

        if (showQueue) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(vm.queue) { i, q ->
                    TrackRow(q, i == vm.queueIndex, vm.isFavorite(q.id), { vm.skipTo(i) }, { vm.toggleFavorite(q) })
                }
            }
        } else if (t != null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Art(t.thumb.replace(Regex("=w\\d+-h\\d+"), "=w800-h800"), 300.dp, corner = 20.dp)
                Spacer(Modifier.height(12.dp))
            }
            // title + artist on the left, favourite heart on the right (like Spotify / YT Music)
            val fav = vm.isFavorite(t.id)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(t.artist, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = { vm.toggleFavorite(t) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (fav) "Remove from favourites" else "Add to favourites",
                        Modifier.size(28.dp), tint = if (fav) Accent else Dim,
                    )
                }
            }
            var editLyrics by remember(t.id) { mutableStateOf(false) }
            val lyricLines = remember(vm.lyricsText) { vm.syncedLyricLines() }
            val plainLines = remember(vm.lyricsText) { vm.plainLyrics().lines().filter { it.isNotBlank() } }
            val activeIndex = vm.activeLyricIndex(pos)
            // Plain (unsynced) lyrics still show one line at a time, roughly placed by progress.
            val plainIndex = if (lyricLines.isEmpty() && plainLines.isNotEmpty() && dur > 1L) {
                ((pos.toFloat() / dur).coerceIn(0f, 0.999f) * plainLines.size).toInt()
            } else -1
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 3.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("LYRICS", color = Accent, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text(vm.lyricsSource.ifBlank { if (vm.lyricsLoading) "Searching sources" else "" }, color = Dim, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { editLyrics = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text("Edit", color = Accent, style = MaterialTheme.typography.labelMedium)
                }
            }
            // A single lyric line above the song title - only the line that is being sung right now.
            Box(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                when {
                    vm.lyricsLoading -> CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
                    vm.lyricsNotFound || vm.lyricsText.isBlank() -> Text("Lyrics not found", color = Dim, style = MaterialTheme.typography.bodySmall)
                    lyricLines.isNotEmpty() -> {
                        if (activeIndex < 0) LyricsGapDots()
                        else Text(
                            lyricLines[activeIndex.coerceIn(0, lyricLines.lastIndex)].second,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    plainLines.isNotEmpty() -> Text(
                        plainLines[plainIndex.coerceIn(0, plainLines.lastIndex)],
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (editLyrics) {
                AlertDialog(
                    onDismissRequest = { editLyrics = false },
                    title = { Text("Lyrics") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = vm.lyricsText,
                                onValueChange = vm::saveLyrics,
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Paste plain lyrics or timestamped LRC lyrics…") },
                                minLines = 4, maxLines = 8,
                            )
                            Text("Synced format: [mm:ss.xx] lyric line", color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = { TextButton(onClick = { editLyrics = false }) { Text("Done", color = Accent) } },
                )
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { Text("Nothing playing", color = Dim) }
        }

        // seek bar
        Slider(
            value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); pos = (dragValue * dur).toLong(); dragging = false },
            colors = SliderDefaults.colors(
                thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Dim.copy(alpha = 0.28f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(fmtTime(if (dragging) (dragValue * dur).toLong() else pos), color = Dim, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f))
            Text(fmtTime(dur), color = Dim, style = MaterialTheme.typography.bodySmall)
        }

        // transport
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (vm.shuffle) Accent else Dim) }
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground) }
            Box(Modifier.size(72.dp).background(Accent, androidx.compose.foundation.shape.CircleShape), Alignment.Center) {
                if (vm.isLoading) CircularProgressIndicator(modifier = Modifier.size(32.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 3.dp)
                else IconButton(onClick = vm::togglePlay, modifier = Modifier.size(72.dp)) {
                    Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Play",
                        Modifier.size(40.dp), tint = androidx.compose.ui.graphics.Color.White)
                }
            }
            IconButton(onClick = vm::next, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onBackground) }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat", tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Dim else Accent,
                )
            }
        }
        Box(Modifier.size(20.dp))
    }
    }
}

// ---------------------------------------------------------------- Settings

/** Rounded card used for every settings section. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface1).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** Pick-one chip row. */
@Composable
private fun SettingsChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val shape = RoundedCornerShape(50)
            Text(
                label,
                modifier = Modifier.clip(shape)
                    .background(if (selected == i) Accent else Surface2)
                    .border(1.dp, if (selected == i) Accent else Surface2, shape)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color = if (selected == i) Color.White else MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: PlayerVM, onLogin: () -> Unit) {
    val context = LocalContext.current
    var cacheSize by remember { mutableLongStateOf(0L) }
    var confirmReset by remember { mutableStateOf(false) }
    fun measureCache() {
        cacheSize = runCatching {
            val dir = context.cacheDir
            fun size(f: java.io.File): Long = if (f.isDirectory) f.listFiles()?.sumOf(::size) ?: 0L else f.length()
            size(dir)
        }.getOrDefault(0L)
    }
    LaunchedEffect(Unit) { measureCache() }
    fun fmtBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
    fun fmtListening(ms: Long): String {
        val mins = ms / 60_000
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 -> "${h} h ${m} m"
            mins > 0 -> "$m min"
            else -> "Under a minute"
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(top = 24.dp, bottom = 4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Accent)
                Text("Make Rustym yours", color = Dim)
            }
        }

        // ---- Account ----
        item { SectionTitle("Account") }
        item {
            SettingsCard {
                val acc = vm.account
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (acc != null) {
                        Art(acc.photo, 48.dp, corner = 24.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(acc.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                            Text("YouTube Music connected", color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = vm::signOut) { Text("Sign out", color = Dim) }
                    } else {
                        Icon(Icons.Filled.AccountCircle, null, Modifier.size(48.dp), tint = Accent)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                            Text("Sync your liked songs and playlists from YouTube Music", color = Dim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = onLogin) { Text("Sign in", color = Accent) }
                    }
                }
            }
        }

        // ---- Appearance ----
        item { SectionTitle("Appearance") }
        item {
            SettingsCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                SettingsChips(AllPalettes.map { it.name }, AllPalettes.indexOfFirst { it.name == ThemeStore.palette.name }) { i -> ThemeStore.select(AllPalettes[i]) }
                Text("Accent colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemeStore.accentChoices.forEach { colour ->
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(colour)
                                .border(if (ThemeStore.palette.accent == colour) 3.dp else 1.dp,
                                    if (ThemeStore.palette.accent == colour) MaterialTheme.colorScheme.onBackground else Dim, CircleShape)
                                .clickable { ThemeStore.selectAccent(colour) }
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Glass effects", style = MaterialTheme.typography.titleSmall)
                        Text("Use translucent player surfaces", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(checked = ThemeStore.glassEffects, onCheckedChange = ThemeStore::updateGlassEffects)
                }
            }
        }

        // ---- Playback ----
        item { SectionTitle("Playback") }
        item {
            SettingsCard {
                Text("Playback speed", style = MaterialTheme.typography.titleMedium)
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
                SettingsChips(speeds.map { "${it}x" }, speeds.indexOfFirst { it == vm.playbackSpeed }.coerceAtLeast(0)) { i -> vm.updatePlaybackSpeed(speeds[i]) }
                Text("Skip fade: ${vm.crossfadeSeconds}s", color = Dim, style = MaterialTheme.typography.bodySmall)
                Slider(value = vm.crossfadeSeconds.toFloat(), onValueChange = { vm.setCrossfade(it.toInt()) }, valueRange = 0f..8f)
                Text("Sleep timer", style = MaterialTheme.typography.titleMedium)
                val sleep = listOf(0, 15, 30, 45, 60)
                SettingsChips(sleep.map { if (it == 0) "Off" else "${it}m" }, sleep.indexOf(vm.sleepTimerMinutes).coerceAtLeast(0)) { i -> vm.setSleepTimer(sleep[i]) }
                Text("Start screen", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                Text("Choose where Rustym opens", color = Dim, style = MaterialTheme.typography.bodySmall)
                val tabs = listOf("Home", "Search", "Device", "Library")
                SettingsChips(tabs, vm.startTab.coerceIn(0, tabs.lastIndex)) { i -> vm.setStartTab(i) }
            }
        }

        // ---- Stats ----
        item { SectionTitle("Your stats") }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("${vm.trackedSongCount}", style = MaterialTheme.typography.headlineSmall, color = Accent)
                        Text("Songs played", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(fmtListening(vm.totalListeningMs), style = MaterialTheme.typography.headlineSmall, color = Accent)
                        Text("Listening time", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Your most played songs live in Library > Stats.", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }

        // ---- Data & storage ----
        item { SectionTitle("Data & storage") }
        item {
            SettingsCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Temporary cache", style = MaterialTheme.typography.titleMedium)
                        Text(fmtBytes(cacheSize), color = Accent, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }; measureCache() }) { Text("Clear", color = Accent) }
                    TextButton(onClick = { measureCache() }) { Text("Refresh", color = Dim) }
                }
                Divider(color = Surface2, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Saved lyrics", style = MaterialTheme.typography.titleMedium)
                        Text("Cached lyrics from every source", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.clearLyricsCache() }) { Text("Clear", color = Accent) }
                }
                Divider(color = Surface2, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Listening history", style = MaterialTheme.typography.titleMedium)
                        Text("${vm.recent.size} recent songs on this device", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.clearRecent() }) { Text("Clear", color = Dim) }
                }
                Divider(color = Surface2, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Play statistics", style = MaterialTheme.typography.titleMedium)
                        Text("Play counts and total listening time", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { confirmReset = true }) { Text("Reset", color = Dim) }
                }
            }
        }

        // ---- About ----
        item { SectionTitle("About") }
        item {
            SettingsCard {
                Text("Rustym for Android", style = MaterialTheme.typography.titleMedium)
                Text("Version 1.3.0 · Music, without distractions.", color = Dim, style = MaterialTheme.typography.bodySmall)
                Text("Lyrics come from Unison, Better Lyrics, KuGou, LRCLIB, Lyrics.ovh and friends. Audio streams directly from YouTube Music.", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset play statistics?") },
            text = { Text("Your per-song play counts and total listening time will be erased. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.resetPlayStats(); confirmReset = false }) { Text("Reset", color = Accent) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = Dim) } },
        )
    }
}
