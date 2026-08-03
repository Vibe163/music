package com.localmusic.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 酷狗蓝主色
private val KugouBlue = Color(0xFF2CA2F9)
private val KugouBlueDark = Color(0xFF169CDD)
private val KugouBlueLight = Color(0xFF4FB5FF) // 深色模式下更亮的蓝

// 深色
private val DarkBackground = Color(0xFF0F1419)
private val DarkSurface = Color(0xFF1A1F26)
private val DarkSurfaceVariant = Color(0xFF2A323C)
private val DarkOnSurfaceVariant = Color(0xFF9AA4B0)

// 浅色
private val LightBackground = Color(0xFFF5F7FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEEF2F6)
private val LightOnSurfaceVariant = Color(0xFF5A6470)

private val DarkColors = darkColorScheme(
    primary = KugouBlueLight,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0E5A8A),
    onPrimaryContainer = Color(0xFFD6EBFB),
    secondary = Color(0xFF9AA4B0),
    background = DarkBackground,
    onBackground = Color(0xFFE8EDF2),
    surface = DarkSurface,
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF3A434E)
)

private val LightColors = lightColorScheme(
    primary = KugouBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EBFB),
    onPrimaryContainer = Color(0xFF0E5A8A),
    secondary = Color(0xFF5A6470),
    background = LightBackground,
    onBackground = Color(0xFF1A1F26),
    surface = LightSurface,
    onSurface = Color(0xFF1A1F26),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFCDD4DC)
)

@Composable
fun LocalMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
