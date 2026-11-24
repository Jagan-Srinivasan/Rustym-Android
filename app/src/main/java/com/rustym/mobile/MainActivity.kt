package com.rustym.mobile

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import com.rustym.mobile.data.LocalMusic
import androidx.compose.material.icons.filled.PhoneAndroid
import com.rustym.mobile.ui.LocalScreen
import com.rustym.mobile.ui.LoginScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rustym.mobile.ui.Accent
import com.rustym.mobile.ui.Bg
import com.rustym.mobile.ui.HomeScreen
import com.rustym.mobile.ui.LibraryScreen
import com.rustym.mobile.ui.MiniPlayer
import com.rustym.mobile.ui.NowPlayingScreen
import com.rustym.mobile.ui.PlayerVM
import com.rustym.mobile.ui.RustymTheme
import com.rustym.mobile.ui.SearchScreen
import com.rustym.mobile.ui.SignInNoticeDialog
import com.rustym.mobile.ui.SettingsScreen
import com.rustym.mobile.ui.Surface2
import com.rustym.mobile.ui.Dim

class MainActivity : ComponentActivity() {
    private val player: PlayerVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RustymTheme { RustymRoot() } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Open with Rustym" on an audio file from a file manager, browser or chat app. */
    private fun handleIntent(i: Intent?) {
        val uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW) return
        player.playOne(LocalMusic.fromUri(this, uri))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Search("Search", Icons.Filled.Search),
    Local("Device", Icons.Filled.PhoneAndroid),
    Library("Library", Icons.Filled.LibraryMusic),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
private fun RustymRoot(vm: PlayerVM = viewModel()) {
    var tab by remember { mutableStateOf(Tab.entries[vm.startTab.coerceIn(0, Tab.entries.lastIndex)]) }
    var showPlayer by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(false) }
    var showSignInNotice by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Android 13+: notification permission is needed to show the playback controls.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // The whole app wears the colour of the last played song, from the very first frame.
    LaunchedEffect(Unit) {
        vm.applyLastPlayedArtworkColor(vm.recent.firstOrNull()?.thumb)
    }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    BackHandler(enabled = showLogin) { showLogin = false }
    BackHandler(enabled = showPlayer && !showLogin) { showPlayer = false }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Bg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    MiniPlayer(vm) { showPlayer = true }
                    RustymNavBar(tab) { tab = it }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm) { showSignInNotice = true }
                    Tab.Search -> SearchScreen(vm)
                    Tab.Local -> LocalScreen(vm)
                    Tab.Library -> LibraryScreen(vm)
                    Tab.Settings -> SettingsScreen(vm) { showSignInNotice = true }
                }
            }
        }
        if (showPlayer) NowPlayingScreen(vm) { showPlayer = false }
        if (showSignInNotice) {
            SignInNoticeDialog(
                onProceed = { showSignInNotice = false; showLogin = true },
                onDismiss = { showSignInNotice = false },
            )
        }
        if (showLogin) LoginScreen(vm) { showLogin = false }
    }
}

/** Floating pill nav bar: each tab is an icon in a soft pill (accent when selected) + label. */
@Composable
private fun RustymNavBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        shape = RoundedCornerShape(28.dp),
        color = Surface2,
        tonalElevation = 2.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Tab.entries.forEach { t ->
                val selected = tab == t
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(t) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Accent.copy(alpha = 0.16f) else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Icon(t.icon, t.label, tint = if (selected) Accent else Dim, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Accent else Dim,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }
    }
}
