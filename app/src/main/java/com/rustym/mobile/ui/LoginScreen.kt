package com.rustym.mobile.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rustym.mobile.data.Auth
import com.rustym.mobile.data.Ytm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Google refuses sign-in from WebViews that identify as one ("; wv" / "Version/4.0"), so present plain Chrome.
private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com"

private fun webCookies(): Map<String, String> {
    val cm = CookieManager.getInstance()
    val merged = LinkedHashMap<String, String>()
    for (u in listOf("https://www.youtube.com", "https://music.youtube.com")) {
        cm.getCookie(u)?.let { merged.putAll(Auth.parse(it)) }
    }
    return merged
}

/**
 * Sign in to Google inside the app. The page is the real Google sign-in (password, 2-step verification,
 * phone prompt...). Once the YouTube session cookies appear they are verified against YouTube Music and stored encrypted.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(vm: PlayerVM, onClose: () -> Unit) {
    var status by remember { mutableStateOf("Sign in with the Google account you use for YouTube Music") }
    var busy by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val scope = rememberCoroutineScope()

    fun finish(cookies: Map<String, String>, fromWeb: Boolean) {
        if (busy) return
        busy = true
        status = "Signed in - checking your account…"
        scope.launch {
            val previous = Auth.cookies()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Auth.save(cookies)
                    Ytm.accountInfo()
                }
            }
            result.onSuccess {
                vm.onSignedIn(it)
                onClose()
            }.onFailure {
                // Don't keep a session YouTube does not accept.
                if (previous.isEmpty()) Auth.clear() else Auth.save(previous)
                busy = false
                status = if (fromWeb) "Almost there - " + (it.message ?: "waiting for YouTube…")
                else "That cookie was not accepted: " + (it.message ?: "unknown error")
            }
        }
    }

    // Start with a clean browser so an old Google session is never silently reused.
    DisposableEffect(Unit) {
        CookieManager.getInstance().removeAllCookies(null)
        onDispose { webView?.apply { stopLoading(); destroy() } }
    }

    // Poll for the session cookies while the user signs in.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            if (!busy) {
                val c = webCookies()
                if (Auth.hasLoginCookie(c)) finish(c, fromWeb = true)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            Text("Sign in to Google", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { showPaste = true }) { Text("Trouble?", color = Dim) }
        }
        Text(status, color = Dim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CHROME_UA
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
        )
    }

    if (showPaste) PasteCookieDialog(
        onDismiss = { showPaste = false },
        onSubmit = { text ->
            showPaste = false
            val c = Auth.parse(text.trim().removePrefix("Cookie:").trim())
            if (Auth.hasLoginCookie(c)) finish(c, fromWeb = false)
            else status = "That text has no Google login cookie (SAPISID). Copy the whole Cookie header."
        },
    )
}

/** Fallback for the rare phone/Google account where the in-app page is refused. */
@Composable
private fun PasteCookieDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in another way") },
        text = {
            Column {
                Text(
                    "If Google says \"this browser or app may not be secure\", sign in on a computer instead: " +
                        "open music.youtube.com while signed in, press F12 → Network, click any request to " +
                        "\"music.youtube.com/youtubei\", and copy the value of the \"Cookie\" request header. Paste it here.",
                    style = MaterialTheme.typography.bodySmall, color = Dim,
                )
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().padding(top = 12.dp), placeholder = { Text("Cookie header") }, maxLines = 4)
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank()) { Text("Sign in", color = Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
