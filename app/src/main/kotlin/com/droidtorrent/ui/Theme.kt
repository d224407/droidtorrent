package com.droidtorrent.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.droidtorrent.TorrentEngine

private val Seed = Color(0xFFFFC107)

/** Preset accent seeds (ARGB). 0 keeps the original colors. */
val AccentPresets = listOf(
    0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(),
    0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF00897B.toInt(),
    0xFF43A047.toInt(), 0xFFFB8C00.toInt(), 0xFFFFC107.toInt(),
)

private fun hueOf(seed: Int): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed, hsv)
    return hsv[0]
}

private fun tone(h: Float, s: Float, l: Float) = Color.hsl(h.coerceIn(0f, 360f), s, l)

private fun accentScheme(seed: Int, dark: Boolean): ColorScheme {
    val h = hueOf(seed)
    val t = (h + 60f) % 360f
    return if (dark) darkColorScheme(
        primary = tone(h, 0.80f, 0.78f), onPrimary = tone(h, 0.70f, 0.15f),
        primaryContainer = tone(h, 0.50f, 0.25f), onPrimaryContainer = tone(h, 0.80f, 0.88f),
        secondary = tone(h, 0.30f, 0.75f), onSecondary = tone(h, 0.30f, 0.15f),
        secondaryContainer = tone(h, 0.20f, 0.28f), onSecondaryContainer = tone(h, 0.30f, 0.88f),
        tertiary = tone(t, 0.40f, 0.78f), onTertiary = tone(t, 0.40f, 0.15f),
        tertiaryContainer = tone(t, 0.30f, 0.27f), onTertiaryContainer = tone(t, 0.40f, 0.88f),
        background = tone(h, 0.10f, 0.08f), onBackground = tone(h, 0.10f, 0.90f),
        surface = tone(h, 0.10f, 0.08f), onSurface = tone(h, 0.10f, 0.90f),
        surfaceVariant = tone(h, 0.10f, 0.25f), onSurfaceVariant = tone(h, 0.10f, 0.78f),
        surfaceContainerLowest = tone(h, 0.10f, 0.06f), surfaceContainerLow = tone(h, 0.10f, 0.10f),
        surfaceContainer = tone(h, 0.10f, 0.13f), surfaceContainerHigh = tone(h, 0.10f, 0.17f),
        surfaceContainerHighest = tone(h, 0.10f, 0.21f),
        outline = tone(h, 0.08f, 0.60f), outlineVariant = tone(h, 0.10f, 0.30f),
    ) else lightColorScheme(
        primary = tone(h, 0.60f, 0.35f), onPrimary = Color.White,
        primaryContainer = tone(h, 0.75f, 0.88f), onPrimaryContainer = tone(h, 0.70f, 0.12f),
        secondary = tone(h, 0.25f, 0.38f), onSecondary = Color.White,
        secondaryContainer = tone(h, 0.35f, 0.88f), onSecondaryContainer = tone(h, 0.30f, 0.12f),
        tertiary = tone(t, 0.35f, 0.38f), onTertiary = Color.White,
        tertiaryContainer = tone(t, 0.45f, 0.88f), onTertiaryContainer = tone(t, 0.40f, 0.12f),
        background = tone(h, 0.20f, 0.98f), onBackground = tone(h, 0.10f, 0.10f),
        surface = tone(h, 0.20f, 0.98f), onSurface = tone(h, 0.10f, 0.10f),
        surfaceVariant = tone(h, 0.20f, 0.90f), onSurfaceVariant = tone(h, 0.10f, 0.30f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = tone(h, 0.20f, 0.96f),
        surfaceContainer = tone(h, 0.20f, 0.94f), surfaceContainerHigh = tone(h, 0.20f, 0.92f),
        surfaceContainerHighest = tone(h, 0.20f, 0.90f),
        outline = tone(h, 0.08f, 0.45f), outlineVariant = tone(h, 0.15f, 0.80f),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val accent by TorrentEngine.settings.collectAsStateWithLifecycle()
    val scheme = when {
        accent.accent != 0 -> accentScheme(accent.accent, dark)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Seed)
        else -> lightColorScheme(primary = Color(0xFF7A5900))
    }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
