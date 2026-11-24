package com.rustym.mobile.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Divider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import com.rustym.mobile.playback.AudioCache
import com.rustym.mobile.playback.AudioFx
import com.rustym.mobile.data.Yt
import com.rustym.mobile.data.Ytm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

// ---------------------------------------------------------------- Home

private val HomeMoods = linkedMapOf(
    "All" to "top hits this week",
    "Relax" to "relaxing soft songs",
    "Sleep" to "sleep music calm",
    "Energize" to "workout energy hits",
    "Romance" to "romantic love songs",
)

@Composable
fun HomeScreen(vm: PlayerVM, onLogin: () -> Unit) {
    var mood by remember { mutableStateOf("All") }
    var picks by remember { mutableStateOf<List<Track>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(mood, reload) {
        failed = false
        picks = null
        picks = withContext(Dispatchers.IO) { runCatching { Yt.search(HomeMoods[mood] ?: "top hits this week") }.getOrNull() }
        if (picks == null) failed = true
    }

    // The accent already follows the last played song (app-wide artwork tint).
    val tint = Accent

    LazyColumn(Modifier.fillMaxSize()) {
        // ---- greeting header, tinted with the last played song
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val first = vm.account?.name?.takeIf { it != "Signed in" }?.substringBefore(' ')
                    Text(if (first != null) "Hi, $first" else "Rustym",
                        style = MaterialTheme.typography.headlineMedium, color = tint)
                    Text(
                        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                            in 5..11 -> "Good morning. Start the day with music."
                            in 12..16 -> "Good afternoon. Your music is waiting."
                            in 17..21 -> "Good evening. Unwind with a few songs."
                            else -> "Up late? Nights sound better with music."
                        },
                        color = Dim,
                    )
                }
                AccountButton(vm, onLogin)
            }
        }
        // ---- mood filter chips
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeMoods.keys.forEach { m ->
                    FilterChip(
                        selected = mood == m,
                        onClick = { mood = m },
                        label = { Text(m) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Surface2,
                            labelColor = MaterialTheme.colorScheme.onBackground,
                            selectedContainerColor = tint,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
        }
        // ---- quick picks: large artwork cards
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick picks", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                val p = picks
                if (p != null && p.isNotEmpty()) TextButton(onClick = { vm.play(p.shuffled(), 0) }) { Text("Play all", color = tint) }
            }
        }
        item {
            val p = picks
            when {
                failed -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Couldn't load picks. Check your connection.", color = Dim)
                    TextButton(onClick = { reload++ }) { Text("Retry", color = tint) }
                }
                p == null -> Box(Modifier.fillMaxWidth().height(150.dp), Alignment.Center) { CircularProgressIndicator(color = tint) }
                p.isEmpty() -> EmptyNote("Nothing here yet - try another mood.")
                else -> LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(p.take(12), key = { i, t -> "qp-$i-${t.id}" }) { i, t ->
                        Box(
                            Modifier
                                .width(180.dp)
                                .height(220.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    val list = p.take(12)
                                    vm.play(list, list.indexOfFirst { it.id == t.id }.coerceAtLeast(0))
                                },
                        ) {
                            coil.compose.AsyncImage(
                                model = t.thumb.replace(Regex("=w\\d+-h\\d+"), "=w544-h544"),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                                ),
                            )
                            Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                                Text(t.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(t.artist, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        // ---- recently played
        if (vm.recent.isNotEmpty()) {
            item { SectionTitle("Recently played") }
            val list = vm.recent.take(8)
            itemsIndexed(list, key = { _, t -> "r" + t.id }) { i, t ->
                TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) })
            }
        }
        item { Box(Modifier.size(24.dp)) }
    }
}


// ---------------------------------------------------------------- Search

private val ExploreMoods = listOf(
    "Trending hits", "Punjabi hits", "Telugu hits", "Tamil hits", "Bollywood 2000s",
    "Love songs", "Workout", "Chill lo-fi", "Party", "Sad songs", "Road trip", "Classics",
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: PlayerVM) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("rustym_settings", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var recent by remember { mutableStateOf(prefs.getString("recent_searches", "")!!.split("|").filter { it.isNotBlank() }) }
    var trending by remember { mutableStateOf<List<Track>?>(null) }
    val scope = rememberCoroutineScope()
    val focus: FocusManager = LocalFocusManager.current

    fun saveRecent(q: String) {
        val list = (listOf(q) + recent.filter { it != q }).take(8)
        recent = list
        prefs.edit().putString("recent_searches", list.joinToString("|")).apply()
    }

    fun clearRecent() {
        recent = emptyList()
        prefs.edit().remove("recent_searches").apply()
    }

    fun doSearch(q: String = query.trim()) {
        if (q.isEmpty()) return
        query = q
        focus.clearFocus()
        loading = true
        error = null
        suggestions = emptyList()
        saveRecent(q)
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Yt.search(q) } }
            loading = false
            r.onSuccess { results = it; vm.prefetchTracks(it.take(7)) }
                .onFailure { error = "Search failed: ${it.message ?: "network error"}" }
        }
    }

    // Live YouTube Music suggestions while typing (debounced).
    LaunchedEffect(query, loading) {
        if (loading || query.isBlank()) { suggestions = emptyList(); return@LaunchedEffect }
        delay(280)
        suggestions = withContext(Dispatchers.IO) {
            runCatching { Ytm.searchSuggestions(query.trim()) }.getOrDefault(emptyList())
        }
    }

    // Trending picks for the idle/explore state.
    LaunchedEffect(Unit) {
        if (trending == null) {
            trending = withContext(Dispatchers.IO) { runCatching { Yt.search("top songs this week").take(12) }.getOrNull() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; results = null; error = null },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
            placeholder = { Text("Songs, artists, moods…") },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = if (query.isNotBlank()) Accent else Dim) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { query = ""; results = null; suggestions = emptyList() }) {
                    Icon(Icons.Filled.Clear, "Clear")
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)

        val list = results
        when {
            error != null -> {
                EmptyNote(error!!)
                TextButton(onClick = { error = null; doSearch(query.trim()) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Retry", color = Accent) }
            }
            list != null && list.isEmpty() -> EmptyNote("Nothing found for \"$query\".")
            list != null -> {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Songs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { results = null; query = ""; suggestions = emptyList() }) { Text("Back to explore", color = Accent) }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(list, key = { i, t -> "$i-${t.id}" }) { i, t ->
                        TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
                    }
                }
            }
            query.isNotBlank() && suggestions.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { doSearch() }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = Accent)
                        Text("Search for \"$query\"", Modifier.padding(start = 14.dp), style = MaterialTheme.typography.bodyLarge, color = Accent)
                    }
                }
                items(suggestions, key = { "s-$it" }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { doSearch(s) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.History, null, Modifier.size(20.dp), tint = Dim)
                        Text(s, Modifier.padding(start = 14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            else -> {
                // ---- explore: recent searches, moods and trending picks
                if (recent.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { clearRecent() }) { Text("Clear", color = Dim) }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { q ->
                            FilterChip(
                                selected = false,
                                onClick = { doSearch(q) },
                                label = { Text(q, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp), tint = Dim) },
                            )
                        }
                    }
                }
                SectionTitle("Browse")
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ExploreMoods.forEach { mood ->
                        FilterChip(
                            selected = false,
                            onClick = { doSearch(mood) },
                            label = { Text(mood) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Surface2,
                                labelColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    }
                }
                SectionTitle("Trending now")
                val tr = trending
                when {
                    tr == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(color = Accent) }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(tr, key = { i, t -> "tr-$i-${t.id}" }) { i, t ->
                            TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(tr, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
                        }
                        item { Box(Modifier.size(16.dp)) }
                    }
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

/** Icon for an audio output-device type. */
private fun outputDeviceIcon(type: Int): ImageVector = when (type) {
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> Icons.Filled.Bluetooth
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
    android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> Icons.Filled.Headphones
    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Icons.Filled.Smartphone
    else -> Icons.Filled.Speaker
}

private fun outputDeviceLabel(type: Int): String = when (type) {
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
    else -> "Phone speaker"
}

/** Shown before the Google flow: explains exactly what sign-in is for. */
@Composable
fun SignInNoticeDialog(onProceed: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in with Google?") },
        text = {
            Column {
                Text("Rustym uses Google sign-in for one thing only:")
                Spacer(Modifier.height(6.dp))
                Text("• Fetching your YouTube Music liked songs", color = Dim)
                Text("• Your playlists and recommendations", color = Dim)
                Spacer(Modifier.height(6.dp))
                Text("No other personal information goes out of this app. Your session is stored encrypted on this device (Android Keystore), is never sent anywhere else, and is never seen by the developer.", color = Dim)
                Spacer(Modifier.height(6.dp))
                Text("You can also use Rustym fully without signing in - search, playback, local music and offline favourites all work.", color = Dim)
            }
        },
        confirmButton = { TextButton(onClick = onProceed) { Text("Continue", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = Dim) } },
    )
}

/** Lists the audio output devices (speaker, wired, Bluetooth, USB) attached to the phone. */
@Composable
private fun OutputDevicesDialog(vm: PlayerVM, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val am = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val devices = remember {
        val wanted = setOf(
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        )
        am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in wanted }.toList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Output devices") },
        text = {
            Column {
                Text("Tap a device to route audio there", color = Dim, style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth().clickable { vm.routeAudioTo(null); onDismiss() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(outputDeviceIcon(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER), null, Modifier.size(22.dp), tint = Accent)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("Default (system)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Follow the system output", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                }
                devices.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.routeAudioTo(d); onDismiss() }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(outputDeviceIcon(d.type), null, Modifier.size(22.dp), tint = Accent)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(d.productName.ifBlank { outputDeviceLabel(d.type) }.toString(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(outputDeviceLabel(d.type), color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                    }
                    onDismiss()
                }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Bluetooth settings", color = Accent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Accent) } },
    )
}

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
    Surface(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .pointerInput(vm) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag += amount },
                    onDragEnd = { if (drag < -48f) vm.next() else if (drag > 48f) vm.previous(); drag = 0f },
                    onDragCancel = { drag = 0f },
                )
            }
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = Surface2.copy(alpha = if (ThemeStore.glassEffects) 0.82f else 1f),
        tonalElevation = 2.dp,
    ) {
        Column {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(t.thumb, 46.dp, corner = 12.dp)
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
        LinearProgressIndicator(
            progress = { (pos.toFloat() / dur).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(2.dp).clip(RoundedCornerShape(2.dp)),
            color = Accent,
            trackColor = Color.Transparent,
            gapSize = 0.dp,
        )
        }
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
    var showOutput by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }

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
        // Material 3 top app bar: collapse · Now Playing + context · headphones = output devices
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (showQueue) "Up next" else "Now Playing", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                    Text(if (showQueue) "Your queue" else vm.queue.takeIf { it.size > 1 }?.let { "Track ${vm.queueIndex + 1} of ${it.size}" } ?: "Rustym", color = Dim, style = MaterialTheme.typography.bodySmall)
                }
            },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onBackground) }
            },
            actions = {
                IconButton(onClick = { showOutput = true }) {
                    Icon(Icons.Filled.Headphones, "Output devices", Modifier.size(26.dp), tint = Accent)
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
        )

        if (showQueue) {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(vm.queue) { i, q ->
                    TrackRow(q, i == vm.queueIndex, vm.isFavorite(q.id), { vm.skipTo(i) }, { vm.toggleFavorite(q) })
                }
            }
        } else if (t != null) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Art(t.thumb.replace(Regex("=w\\d+-h\\d+"), "=w800-h800"), 320.dp, corner = 26.dp)
            }
            var editLyrics by remember(t.id) { mutableStateOf(false) }
            val lyricLines = remember(vm.lyricsText) { vm.syncedLyricLines() }
            val plainLines = remember(vm.lyricsText) { vm.plainLyrics().lines().filter { it.isNotBlank() } }
            val activeIndex = vm.activeLyricIndex(pos)
            // Plain (unsynced) lyrics still show one line, roughly placed by progress.
            val plainIndex = if (lyricLines.isEmpty() && plainLines.isNotEmpty() && dur > 1L) {
                ((pos.toFloat() / dur).coerceIn(0f, 0.999f) * plainLines.size).toInt()
            } else -1
            val currentLine: String? = when {
                lyricLines.isNotEmpty() -> lyricLines.getOrNull(activeIndex.coerceAtLeast(0))?.second
                plainIndex >= 0 -> plainLines.getOrNull(plainIndex)
                else -> null
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp, start = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(24.dp), contentAlignment = Alignment.CenterStart) {
                    when {
                        lyricLines.isNotEmpty() && activeIndex < 0 -> LyricsGapDots()
                        currentLine != null -> Text(
                            currentLine,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.pointerInput(t.id) { detectTapGestures(onLongPress = { editLyrics = true }) },
                        )
                    }
                }
                if (vm.lyricsText.isNotBlank()) {
                    IconButton(onClick = { editLyrics = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.Edit, "Edit lyrics", Modifier.size(16.dp), tint = Dim)
                    }
                }
            }
            // title + artist on the left, favourite heart on the right (like Spotify / YT Music)
            val fav = vm.isFavorite(t.id)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(t.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground)
                    Text(t.artist, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 3.dp))
                }
                IconButton(onClick = { vm.toggleFavorite(t) }, modifier = Modifier.size(48.dp)) {
                    Icon(
                        if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (fav) "Remove from favourites" else "Add to favourites",
                        Modifier.size(28.dp), tint = if (fav) Accent else Dim,
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
            // slim, premium slider: hairline track with a small thumb
            thumb = {
                Box(Modifier.size(14.dp).clip(CircleShape).background(Accent))
            },
            track = { state ->
                val frac = state.value.coerceIn(0.004f, 1f)
                Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Dim.copy(alpha = 0.25f)))
                    Box(Modifier.fillMaxWidth(frac).height(3.dp).clip(CircleShape).background(Accent))
                }
            },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(fmtTime(if (dragging) (dragValue * dur).toLong() else pos), color = Dim, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f))
            Text(fmtTime(dur), color = Dim, style = MaterialTheme.typography.bodySmall)
        }

        // transport
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            // Material 3 tonal icon buttons give the pill shape, accent fill and ripple for free.
            FilledIconButton(onClick = vm::previous, modifier = Modifier.size(72.dp, 56.dp), shape = RoundedCornerShape(50)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(32.dp))
            }
            FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(116.dp, 64.dp), shape = RoundedCornerShape(50)) {
                if (vm.isLoading) CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp)
                else Icon(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Play", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = vm::next, modifier = Modifier.size(72.dp, 56.dp), shape = RoundedCornerShape(50)) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(32.dp))
            }
        }
        // utility row: shuffle · speed · repeat
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) { Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (vm.shuffle) Accent else Dim) }
            IconButton(onClick = { showQueue = !showQueue }) { Icon(Icons.Filled.QueueMusic, "Queue", tint = if (showQueue) Accent else Dim) }
            IconButton(onClick = { showFx = true }, modifier = Modifier.size(26.dp)) { Icon(Icons.Filled.Tune, "Sound effects", Modifier.size(22.dp), tint = Dim) }
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
            AssistChip(onClick = { vm.updatePlaybackSpeed(speeds[(speeds.indexOf(vm.playbackSpeed).coerceAtLeast(0) + 1) % speeds.size]) }, label = { Text("${vm.playbackSpeed}x", color = Accent) })
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat", tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Dim else Accent,
                )
            }
        }
        Box(Modifier.size(14.dp))
        if (showOutput) OutputDevicesDialog(vm) { showOutput = false }
        if (showFx) SoundFxDialog { showFx = false }
    }
    }
}

// ---- Sound effects: equalizer, bass boost, surround, 8D --------------------

private fun fmtDb(mb: Int): String = "%.1f dB".format(mb / 1000f)

/** Vertical slider for the equalizer bands (rotated M3 slider). */
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.width(150.dp).graphicsLayer { rotationZ = -90f },
        )
    }
}

/** The full sound-effects panel, shared by Settings and the Now Playing popover. */
@Composable
fun SoundControls() {
    val range = AudioFx.bandRange
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Equalizer", style = MaterialTheme.typography.titleSmall)
                Text("Shape the sound across ${AudioFx.bandCount} bands", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = AudioFx.eqEnabled, onCheckedChange = { AudioFx.setEqualizerEnabled(it) })
        }
        if (AudioFx.eqEnabled && AudioFx.bandGains.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                AudioFx.bandGains.forEachIndexed { band, gain ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(fmtDb(gain), style = MaterialTheme.typography.labelSmall, color = Accent)
                        VerticalSlider(
                            value = gain.toFloat().coerceIn(range.first.toFloat(), range.second.toFloat()),
                            onValueChange = { AudioFx.setBandGain(band, it.toInt()) },
                            valueRange = range.first.toFloat()..range.second.toFloat(),
                            modifier = Modifier.width(40.dp).height(150.dp),
                        )
                        Text(AudioFx.bandLabels.getOrNull(band).orEmpty(), style = MaterialTheme.typography.labelSmall, color = Dim)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bass boost", style = MaterialTheme.typography.titleSmall)
                Text("Deep low-end punch", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            Text("${AudioFx.bassPercent}%", color = Accent, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = AudioFx.bassPercent.toFloat(),
            onValueChange = { AudioFx.setBass(it.toInt()) },
            valueRange = 0f..100f,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Surround sound", style = MaterialTheme.typography.titleSmall)
                Text("Widen the stereo field", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            Text("${AudioFx.surroundPercent}%", color = Accent, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = AudioFx.surroundPercent.toFloat(),
            onValueChange = { AudioFx.setSurround(it.toInt()) },
            valueRange = 0f..100f,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("8D audio", style = MaterialTheme.typography.titleSmall)
                Text("The sound rotates around your head", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = AudioFx.eightD, onCheckedChange = { AudioFx.set8D(it) })
        }
    }
}

@Composable
private fun SoundFxDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound effects") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 460.dp)) {
                SoundControls()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Accent) } },
    )
}

/** Standalone "Updates" card: check GitHub releases + how-to-update instructions. */
@Composable
private fun UpdateCard() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
    }
    var checking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until 3) {
            val rv = r.getOrNull(i) ?: 0
            val cv = c.getOrNull(i) ?: 0
            if (rv != cv) return rv > cv
        }
        return false
    }
    SettingsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                Text("Installed version: $versionName", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Filled.Info, "How to update", Modifier.size(20.dp), tint = Dim)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                checking = true
                updateStatus = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val c = java.net.URL("https://api.github.com/repos/Jagan-Srinivasan/Rustym-Android/releases/latest")
                                .openConnection() as java.net.HttpURLConnection
                            c.connectTimeout = 8000
                            c.readTimeout = 8000
                            c.setRequestProperty("Accept", "application/vnd.github+json")
                            val code = c.responseCode
                            if (code == 404) "none" to ""
                            else if (code !in 200..299) "error" to ""
                            else {
                                val body = c.inputStream.bufferedReader().use { it.readText() }
                                val obj = org.json.JSONObject(body)
                                obj.optString("tag_name") to obj.optString("html_url")
                            }
                        }.getOrElse { "error" to "" }
                    }
                    checking = false
                    when {
                        result.first == "none" ->
                            updateStatus = "No new release available."
                        result.first == "error" ->
                            updateStatus = "Couldn't check for updates - check your connection."
                        isNewer(result.first.removePrefix("v").removePrefix("V"), versionName) -> {
                            updateStatus = "New version ${result.first} is available!"
                            updateUrl = result.second.ifBlank { "https://github.com/Jagan-Srinivasan/Rustym-Android/releases/latest" }
                        }
                        else -> updateStatus = "No new release - you're on the latest version ($versionName)."
                    }
                }
            }) { Text("Check for updates", color = Accent) }
            if (checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
            }
        }
        updateStatus?.let { st ->
            Text(st, color = if (st.startsWith("New")) Accent else Dim, style = MaterialTheme.typography.bodySmall)
            updateUrl?.let { url ->
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }) { Text("Open release page", color = Accent) }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("How to update Rustym") },
            text = {
                Column {
                    Text("1. Tap "Check for updates" above.")
                    Spacer(Modifier.height(4.dp))
                    Text("2. If a new version is found, tap "Open release page" - GitHub opens in your browser.")
                    Spacer(Modifier.height(4.dp))
                    Text("3. Download the Rustym.apk attached to that release.")
                    Spacer(Modifier.height(4.dp))
                    Text("4. Open the downloaded file and install it. If Android asks, allow "Install unknown apps" for your browser or file manager.")
                    Spacer(Modifier.height(8.dp))
                    Text("Updating keeps your favourites, history, settings and caches - nothing is lost.", color = Dim)
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Got it", color = Accent) } },
        )
    }
}

// ---------------------------------------------------------------- Settings

/** Material 3 elevated card used for every settings section. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = Surface1,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Material 3 filter chips: pick-one options with native selection styling. */
@Composable
private fun SettingsChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = selected == i,
                onClick = { onSelect(i) },
                label = { Text(label, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Surface2,
                    labelColor = MaterialTheme.colorScheme.onBackground,
                    selectedContainerColor = Accent,
                    selectedLabelColor = Color.White,
                ),
            )
        }
    }
}

@Composable
fun SettingsScreen(vm: PlayerVM, onLogin: () -> Unit) {
    val context = LocalContext.current
    var cacheSize by remember { mutableLongStateOf(0L) }
    var confirmReset by remember { mutableStateOf(false) }
    var songStats by remember { mutableStateOf(0 to 0L) }
    var lyricStats by remember { mutableStateOf(0 to 0L) }
    val scope = rememberCoroutineScope()
    // Cache walks and SimpleCache queries are disk work - never do them on the main thread.
    fun measureCache() {
        scope.launch(Dispatchers.IO) {
            val dir = context.cacheDir
            fun size(f: java.io.File): Long = if (f.isDirectory) f.listFiles()?.sumOf(::size) ?: 0L else f.length()
            val temp = runCatching { size(dir) }.getOrDefault(0L)
            val songs = runCatching { AudioCache.cachedSongs() to AudioCache.cachedBytes() }.getOrDefault(0 to 0L)
            val lyrics = vm.lyricsStats()
            withContext(Dispatchers.Main) {
                cacheSize = temp
                songStats = songs
                lyricStats = lyrics
            }
        }
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Glass effects", style = MaterialTheme.typography.titleSmall)
                        Text("Use translucent player surfaces", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(checked = ThemeStore.glassEffects, onCheckedChange = ThemeStore::updateGlassEffects)
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You colours", style = MaterialTheme.typography.titleSmall)
                        Text("Colours from your wallpaper (Android 12+)", color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.Switch(
                        checked = ThemeStore.useDynamicColors,
                        onCheckedChange = { ThemeStore.updateDynamicColors(it) },
                        enabled = android.os.Build.VERSION.SDK_INT >= 31,
                    )
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
                SettingsChips(tabs, vm.startTab.coerceIn(0, tabs.lastIndex)) { i -> vm.chooseStartTab(i) }
            }
        }

        // ---- Sound ----
        item { SectionTitle("Sound") }
        item {
            SettingsCard { SoundControls() }
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
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                            withContext(Dispatchers.Main) { measureCache() }
                        }
                    }) { Text("Clear", color = Accent) }
                    TextButton(onClick = { measureCache() }) { Text("Refresh", color = Dim) }
                }
                Divider(color = Surface2, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cached songs", style = MaterialTheme.typography.titleMedium)
                        Text("${songStats.first} songs · ${fmtBytes(songStats.second)} downloaded for offline",
                            color = if (songStats.first > 0) Accent else Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                            AudioCache.clear()
                            withContext(Dispatchers.Main) { measureCache() }
                        }
                    }) { Text("Clear", color = Accent) }
                }
                Divider(color = Surface2, thickness = 1.dp)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Saved lyrics", style = MaterialTheme.typography.titleMedium)
                        Text("${lyricStats.first} lyrics cached · ${fmtBytes(lyricStats.second)} from every source",
                            color = if (lyricStats.first > 0) Accent else Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { vm.clearLyricsCache(); measureCache() }) { Text("Clear", color = Accent) }
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
                val versionName = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0.0"
                }
                Text("Version $versionName · Music, without distractions.", color = Dim, style = MaterialTheme.typography.bodySmall)
                Text("Developed by Jagan Srinivasan", color = Dim, style = MaterialTheme.typography.bodySmall)

                val repo = "https://github.com/Jagan-Srinivasan/Rustym-Android"
                Text("github.com/Jagan-Srinivasan/Rustym-Android",
                    color = Accent,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(repo)))
                            }
                        })
                Text("How Rustym works: audio streams from YouTube Music; lyrics come from Unison, Better Lyrics, KuGou, LRCLIB, NetEase and Lyrics.ovh. Nothing is uploaded anywhere - the app only reads public content and your own library.", color = Dim, style = MaterialTheme.typography.bodySmall)
                Text("Google sign-in is used only to fetch your liked songs, playlists and recommendations. Your session is stored encrypted on this device and no personal information leaves the app.", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
        }
        // ---- Updates ----
        item { SectionTitle("Updates") }
        item { UpdateCard() }
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
