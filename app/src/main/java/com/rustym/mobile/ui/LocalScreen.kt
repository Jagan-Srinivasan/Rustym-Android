package com.rustym.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rustym.mobile.data.LocalMusic
import com.rustym.mobile.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Music stored on the phone: everything the media scanner found, or files picked by hand (no permission needed). */
@Composable
fun LocalScreen(vm: PlayerVM) {
    val ctx = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf<List<Track>?>(null) }
    var filter by remember { mutableStateOf("") }
    var scan by remember { mutableStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; asked = true }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val tracks = uris.map { u ->
                runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                LocalMusic.fromUri(ctx, u)
            }
            vm.play(tracks, 0)
        }
    }

    LaunchedEffect(granted, scan) {
        if (granted) songs = withContext(Dispatchers.IO) { LocalMusic.query(ctx) }
    }

    Column(Modifier.fillMaxSize()) {
        Text("On this device", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp))

        if (!granted) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("Play the music stored on your phone", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    "Rustym needs permission to read audio files. It only reads music; nothing is uploaded.",
                    color = Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Button(onClick = {
                    if (asked) ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                    else permLauncher.launch(permission)
                }, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text(if (asked) "Open app settings" else "Allow access to music")
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Or pick files without permission")
                }
            }
            return@Column
        }

        val all = songs
        if (all == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Accent) }
            return@Column
        }

        OutlinedTextField(
            value = filter, onValueChange = { filter = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Filter ${all.size} songs") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (filter.isNotEmpty()) IconButton(onClick = { filter = "" }) { Icon(Icons.Filled.Clear, "Clear") } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, cursorColor = Accent),
        )
        val list = if (filter.isBlank()) all else all.filter { it.title.contains(filter, true) || it.artist.contains(filter, true) }
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { vm.play(list, 0) }, enabled = list.isNotEmpty()) { Text("Play all", color = Accent) }
            TextButton(onClick = { vm.play(list.shuffled(), 0) }, enabled = list.isNotEmpty()) { Text("Shuffle", color = Accent) }
            TextButton(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Pick files", color = Dim) }
            TextButton(onClick = { scan++ }) { Text("Rescan", color = Dim) }
        }
        when {
            all.isEmpty() -> EmptyNote("No music found on this device. Use “Pick files” to choose songs manually.")
            list.isEmpty() -> EmptyNote("No song matches “$filter”.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                    TrackRow(t, vm.current?.id == t.id, vm.isFavorite(t.id), { vm.play(list, i) }, { vm.toggleFavorite(t) }, { vm.addToQueue(t) })
                }
            }
        }
    }
}
