package com.rustym.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** One complete colour set. Every screen reads its colours from the active Palette. */
@Immutable
class Palette(
    val name: String,
    val isLight: Boolean,
    val accent: Color,
    val bg: Color,
    val surface1: Color,
    val surface2: Color,
    val dim: Color,
    val onBg: Color,
)

val MidnightPalette = Palette(
    name = "Midnight", isLight = false,
    accent = Color(0xFFFF3D5E), bg = Color(0xFF0F0F14),
    surface1 = Color(0xFF17171F), surface2 = Color(0xFF20202A),
    dim = Color(0xFF9A9AA8), onBg = Color(0xFFF2F2F6),
)

val AmoledPalette = Palette(
    name = "AMOLED", isLight = false,
    accent = Color(0xFFFF3D5E), bg = Color(0xFF000000),
    surface1 = Color(0xFF0B0B0E), surface2 = Color(0xFF16161B),
    dim = Color(0xFF8E8E9A), onBg = Color(0xFFF2F2F6),
)

val LightPalette = Palette(
    name = "Light", isLight = true,
    accent = Color(0xFFE8274A), bg = Color(0xFFF6F6FA),
    surface1 = Color(0xFFFFFFFF), surface2 = Color(0xFFE9E9F0),
    dim = Color(0xFF6B6B7A), onBg = Color(0xFF14141A),
)

val AllPalettes = listOf(MidnightPalette, AmoledPalette, LightPalette)

/** Holds the chosen theme (observable by Compose) and persists it. */
object ThemeStore {
    private const val PREFS = "rustym_settings"
    private const val KEY = "theme"
    private const val ACCENT_KEY = "accent"
    private const val GLASS_KEY = "glass"
    private const val DYNAMIC_KEY = "dynamic_colors"
    private var prefs: SharedPreferences? = null

    var palette by mutableStateOf(MidnightPalette)
        private set
    var glassEffects by mutableStateOf(true)
        private set
    var useDynamicColors by mutableStateOf(false)
        private set
    private var basePalette: Palette = MidnightPalette

    val accentChoices = listOf(
        Color(0xFFFF3D5E), Color(0xFF7C4DFF), Color(0xFF00BFA5),
        Color(0xFF29B6F6), Color(0xFFFFB300), Color(0xFFFF7043),
        Color(0xFFEC407A), Color(0xFF9CCC65),
    )

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString(KEY, null)
        basePalette = AllPalettes.firstOrNull { it.name == saved } ?: MidnightPalette
        val accent = p.getInt(ACCENT_KEY, basePalette.accent.toArgb())
        glassEffects = p.getBoolean(GLASS_KEY, true)
        useDynamicColors = p.getBoolean(DYNAMIC_KEY, false)
        palette = basePalette.withAccent(Color(accent))
    }

    fun select(p: Palette) {
        basePalette = p
        palette = p.withAccent(palette.accent)
        prefs?.edit()?.putString(KEY, p.name)?.apply()
    }

    fun selectAccent(color: Color) {
        palette = basePalette.withAccent(color)
        prefs?.edit()?.putInt(ACCENT_KEY, color.toArgb())?.apply()
    }

    fun updateGlassEffects(enabled: Boolean) {
        glassEffects = enabled
        prefs?.edit()?.putBoolean(GLASS_KEY, enabled)?.apply()
    }

    /** Material You: derive every colour from the user's wallpaper (Android 12+). */
    fun updateDynamicColors(enabled: Boolean) {
        useDynamicColors = enabled
        prefs?.edit()?.putBoolean(DYNAMIC_KEY, enabled)?.apply()
    }

    fun applyArtworkAccent(color: Color) {
        palette = basePalette.withAccent(color)
    }

    private fun Palette.withAccent(color: Color) = Palette(name, isLight, color, bg, surface1, surface2, dim, onBg)
}

val LocalPalette = staticCompositionLocalOf { MidnightPalette }

// These keep their old names so every existing screen picks up the active theme automatically.
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
val Bg: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.bg
val Surface1: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surface1
val Surface2: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.surface2
val Dim: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.dim

private fun Palette.toScheme(): ColorScheme {
    val base = if (isLight) lightColorScheme() else darkColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = Color.White,
        background = bg,
        onBackground = onBg,
        surface = surface1,
        onSurface = onBg,
        surfaceVariant = surface2,
        onSurfaceVariant = dim,
        secondaryContainer = surface2,
        onSecondaryContainer = onBg,
        surfaceContainerLow = surface1,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface2,
        outline = dim.copy(alpha = 0.55f),
        outlineVariant = dim.copy(alpha = 0.25f),
    )
}

@Composable
fun RustymTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = ThemeStore.palette
    // Material You: when enabled on Android 12+, every token comes from the wallpaper palette.
    val p = if (ThemeStore.useDynamicColors && android.os.Build.VERSION.SDK_INT >= 31) {
        val dyn = if (base.isLight) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
        Palette(
            name = "Material You", isLight = base.isLight,
            accent = dyn.primary, bg = dyn.background,
            surface1 = dyn.surfaceContainerLow, surface2 = dyn.surfaceContainer,
            dim = dyn.onSurfaceVariant, onBg = dyn.onBackground,
        )
    } else base
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(p.bg.toArgb()))
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = p.isLight
                controller.isAppearanceLightNavigationBars = p.isLight
            }
        }
    }
    val scheme = remember(p) { p.toScheme() }
    MaterialTheme(colorScheme = scheme) {
        // Without this, any Text drawn outside a Scaffold/Surface falls back to black.
        CompositionLocalProvider(
            LocalPalette provides p,
            LocalContentColor provides p.onBg,
        ) { content() }
    }
}
