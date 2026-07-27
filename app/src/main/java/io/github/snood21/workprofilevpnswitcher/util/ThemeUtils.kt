package io.github.snood21.workprofilevpnswitcher.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeUtils {

    /**
     * Применить сохранённый режим темы через AppCompatDelegate.
     * Вызывать при старте процесса (Application.onCreate) и после смены темы в настройках.
     */
    fun applyTheme(context: Context) {
        val settings = AppSettings(context)
        val mode = when (settings.themeMode) {
            AppSettings.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppSettings.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
