package io.github.snood21.workprofilevpnswitcher.util

import android.content.Context
import android.util.Log

/**
 * Логирование в logcat (только debug-сборки) и опционально в файл
 * (если включено в настройках, независимо от типа сборки).
 * Требует Logger.init(context) при старте процесса — см. Application.onCreate().
 */
object Logger {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun d(tag: String, msg: () -> String) = log(tag, "D", msg) { text -> Log.d(tag, text) }

    fun w(tag: String, msg: () -> String) = log(tag, "W", msg) { text -> Log.w(tag, text) }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        // Ошибки логируем в logcat всегда
        Log.e(tag, msg, throwable)
        if (loggingEnabled()) {
            val text = msg + (throwable?.let { ": ${it.message}" } ?: "")
            FileLogger.write(appContext, tag, "E", text)
        }
    }

    private fun log(tag: String, level: String, msg: () -> String, toLogcat: (String) -> Unit) {
        if (loggingEnabled()) {
            val text = msg()
            toLogcat(text)
            FileLogger.write(appContext, tag, level, text)
        }
    }

    private fun loggingEnabled(): Boolean =
        ::appContext.isInitialized && AppSettings(appContext).loggingEnabled
}
