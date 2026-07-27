package io.github.snood21.workprofilevpnswitcher.ui

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.FileLogger
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils

class LoggingSettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var switchLoggingEnabled: SwitchCompat
    private lateinit var logMaxSizeContainer: View
    private lateinit var etLogMaxSize: EditText
    private lateinit var btnShowLog: Button

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logging_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.action_logging_settings)

        settings = AppSettings(this)

        switchLoggingEnabled = findViewById(R.id.switch_logging_enabled)
        logMaxSizeContainer = findViewById(R.id.log_max_size_container)
        etLogMaxSize = findViewById(R.id.et_log_max_size)
        btnShowLog = findViewById(R.id.btn_show_log)

        loadState()
        setupListeners()
    }

    override fun onPause() {
        super.onPause()
        saveLogMaxSize()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadState() {
        switchLoggingEnabled.isChecked = settings.loggingEnabled
        etLogMaxSize.setText(settings.logMaxSizeKb.toString())
        logMaxSizeContainer.visibility = if (settings.loggingEnabled) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        switchLoggingEnabled.setOnCheckedChangeListener { _, isChecked ->
            settings.loggingEnabled = isChecked
            logMaxSizeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        btnShowLog.setOnClickListener { showLogDialog() }
    }

    private fun saveLogMaxSize() {
        when (val kb = etLogMaxSize.text.toString().toIntOrNull()) {
            null -> {
                etLogMaxSize.error = getString(R.string.error_invalid_number)
            }
            !in 128..8192 -> {
                etLogMaxSize.setText(settings.logMaxSizeKb.toString())
                etLogMaxSize.error = getString(R.string.error_log_max_size_range)
            }
            else -> {
                settings.logMaxSizeKb = kb
                etLogMaxSize.error = null
            }
        }
    }

    private fun showLogDialog() {
        val textView = TextView(this).apply {
            setTextIsSelectable(true)
            textSize = 12f
            val padding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics
            ).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val scrollView = ScrollView(this).apply { addView(textView) }
        updateLogText(textView)

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.action_show_log))
            .setView(scrollView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.action_clear_log, null)
            .create()

        dialog.show()
        // Переопределяем клик, чтобы диалог не закрывался — только очищаем и обновляем текст
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            FileLogger.clearLog(this)
            updateLogText(textView)
        }
    }

    private fun updateLogText(textView: TextView) {
        val log = FileLogger.readLog(this)
        textView.text = log.ifEmpty { getString(R.string.log_empty) }
    }
}
