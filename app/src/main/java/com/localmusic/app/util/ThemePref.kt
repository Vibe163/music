package com.localmusic.app.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** 主题模式：跟随系统 / 浅色 / 深色。 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 主题模式持久化（SharedPreferences）。 */
object ThemePref {
    private const val PREFS = "theme_prefs"
    private const val KEY = "theme_mode"

    fun read(context: Context): ThemeMode = runCatching {
        ThemeMode.valueOf(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        )
    }.getOrDefault(ThemeMode.SYSTEM)

    fun write(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, mode.name)
            .apply()
    }
}

/** 读取已持久化的主题模式，并在变化时自动保存。 */
@Composable
fun rememberThemeMode(): MutableState<ThemeMode> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(ThemePref.read(context)) }

    LaunchedEffect(state.value) {
        ThemePref.write(context, state.value)
    }

    return state
}
