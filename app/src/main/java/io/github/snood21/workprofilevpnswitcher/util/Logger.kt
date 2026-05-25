package io.github.snood21.workprofilevpnswitcher.util

import android.util.Log
import io.github.snood21.workprofilevpnswitcher.BuildConfig

object Logger {
    fun d(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg())
    }

    fun w(tag: String, msg: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, msg())
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        // Ошибки логируем всегда — они важны и в release
        Log.e(tag, msg, throwable)
    }
}
