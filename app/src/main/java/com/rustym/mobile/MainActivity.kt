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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
                    NavigationBar(containerColor = Surface2, modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(26.dp))) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent, selectedTextColor = Accent,
                                    indicatorColor = Bg, unselectedIconColor = Dim, unselectedTextColor = Dim,
                                ),
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm) { showLogin = true }
                    Tab.Search -> SearchScreen(vm)
                    Tab.Local -> LocalScreen(vm)
                    Tab.Library -> LibraryScreen(vm)
                    Tab.Settings -> SettingsScreen(vm) { showLogin = true }
                }
            }
        }
        if (showPlayer) NowPlayingScreen(vm) { showPlayer = false }
        if (showLogin) LoginScreen(vm) { showLogin = false }
    }
}
