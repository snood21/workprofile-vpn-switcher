package io.github.snood21.workprofilevpnswitcher.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
     * Сменить язык и перезапустить Activity.
     */
    fun changeLanguage(activity: Activity, languageCode: String) {
        val settings = AppSettings(activity)
        settings.appLanguage = languageCode
        val intent = activity.intent
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activity.finish()
        activity.startActivity(intent)
    }
}
