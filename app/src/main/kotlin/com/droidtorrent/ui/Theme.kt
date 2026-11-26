package com.droidtorrent.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Seed = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
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
