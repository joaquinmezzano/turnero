package com.turnero.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val LightColors = lightColorScheme(
    primary = Color(0xFF2E5E4E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2F1D4),
    onPrimaryContainer = Color(0xFF002014),
    secondary = Color(0xFF4C635A),
    tertiary = Color(0xFF3F6375),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF97D5B9),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF14503C),
    onPrimaryContainer = Color(0xFFB2F1D4),
    secondary = Color(0xFFB3CCC1),
    tertiary = Color(0xFFA6CDDF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TurneroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
