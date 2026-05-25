package io.github.snood21.workprofilevpnswitcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.snood21.workprofilevpnswitcher.service.VpnMonitorService
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.Logger

/**
 * Запускает сервис мониторинга при загрузке устройства,
 * если мониторинг был включён до перезагрузки.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val settings = AppSettings(context)
        if (!settings.monitoringEnabled) {
            Logger.d(TAG) {"Monitoring disabled, skipping autostart"}
            return
        }

        Logger.d(TAG) {"Boot completed, starting VpnMonitorService"}
        context.startForegroundService(VpnMonitorService.startIntent(context))
    }
}
