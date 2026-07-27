package io.github.snood21.workprofilevpnswitcher.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils
import io.github.snood21.workprofilevpnswitcher.util.ThemeUtils

class InterfaceSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var btnLanguage: Button
    private lateinit var btnTheme: Button

    // Данные для выбора языка: code → display name
    private val languages by lazy {
        listOf(
            "" to getString(R.string.lang_system),
            "ru" to getString(R.string.lang_ru),
            "en" to getString(R.string.lang_en)
        )
    }

    // Данные для выбора темы: mode → display name
    private val themes by lazy {
        listOf(
            AppSettings.THEME_MODE_SYSTEM to getString(R.string.theme_system),
            AppSettings.THEME_MODE_LIGHT to getString(R.string.theme_light),
            AppSettings.THEME_MODE_DARK to getString(R.string.theme_dark)
        )
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_interface_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.action_interface_settings)

        settings = AppSettings(this)
        btnLanguage = findViewById(R.id.btn_language)
        btnTheme = findViewById(R.id.btn_theme)

        updateLanguageButton()
        btnLanguage.setOnClickListener { showLanguageDialog() }

        updateThemeButton()
        btnTheme.setOnClickListener { showThemeDialog() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showLanguageDialog() {
        val displayNames = languages.map { it.second }.toTypedArray()
        val currentCode = settings.appLanguage
        val currentIndex = languages.indexOfFirst { it.first == currentCode }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.section_language))
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                dialog.dismiss()
                val selectedCode = languages[which].first
                if (selectedCode != currentCode) {
                    LanguageUtils.changeLanguage(this, selectedCode)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateLanguageButton() {
        val currentCode = settings.appLanguage
        val displayName = languages.firstOrNull { it.first == currentCode }?.second
            ?: getString(R.string.lang_system)
        btnLanguage.text = displayName
    }

    private fun showThemeDialog() {
        val displayNames = themes.map { it.second }.toTypedArray()
        val currentMode = settings.themeMode
        val currentIndex = themes.indexOfFirst { it.first == currentMode }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.section_theme))
            .setSingleChoiceItems(displayNames, currentIndex) { dialog, which ->
                dialog.dismiss()
                val selectedMode = themes[which].first
                if (selectedMode != currentMode) {
                    settings.themeMode = selectedMode
                    updateThemeButton()
                    ThemeUtils.applyTheme(this)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateThemeButton() {
        val currentMode = settings.themeMode
        val displayName = themes.firstOrNull { it.first == currentMode }?.second
            ?: getString(R.string.theme_system)
        btnTheme.text = displayName
    }
}
