package io.github.snood21.workprofilevpnswitcher.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import io.github.snood21.workprofilevpnswitcher.ui.MainActivity
import java.util.Locale

object LanguageUtils {

    /**
     * Применить сохранённый язык к контексту.
     * Вызывать в Activity.attachBaseContext().
     */
    fun applyLanguage(base: Context): Context {
        val settings = AppSettings(base)
        val lang = settings.appLanguage
        if (lang.isEmpty()) return base

        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Сменить язык и перезапустить весь стек Activity.
     * Пересоздания только текущей Activity недостаточно: остальные Activity
     * в стеке (например, MainActivity, из которой был открыт этот экран)
     * уже применили старую локаль в своём attachBaseContext и не пересоздаются
     * автоматически при изменении настройки — поэтому перезапускаем задачу целиком.
     */
    fun changeLanguage(activity: Activity, languageCode: String) {
        val settings = AppSettings(activity)
        settings.appLanguage = languageCode

        val intent = Intent(activity, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}
