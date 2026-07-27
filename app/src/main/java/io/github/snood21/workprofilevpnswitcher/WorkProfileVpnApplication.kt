package io.github.snood21.workprofilevpnswitcher

import android.app.Application
import io.github.snood21.workprofilevpnswitcher.util.Logger
import io.github.snood21.workprofilevpnswitcher.util.ThemeUtils

/**
 * Точка входа процесса приложения — выполняется до создания
 * любой Activity/Service/Receiver.
 */
class WorkProfileVpnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.init(this)
        ThemeUtils.applyTheme(this)
    }
}
