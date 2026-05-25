package io.github.snood21.workprofilevpnswitcher.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.RingtoneManager
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.VpnUtils
import io.github.snood21.workprofilevpnswitcher.util.WorkProfileManager
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.util.Logger

/**
 * Отслеживает ручное включение рабочего профиля.
 *
 * Обратная логика: если пользователь включил рабочий профиль вручную,
 * а в это время активен VPN из списка — отключить рабочий профиль снова.
 *
 * Настройка управляется через AppSettings.disableVpnOnProfileEnable.
 */
class WorkProfileReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkProfileReceiver"
        private const val CHANNEL_ID_ALERT = "vpn_block_alert_channel"
        private const val NOTIFICATION_ID_BLOCKED = 1002
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MANAGED_PROFILE_AVAILABLE -> handleProfileEnabled(context)
            Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> {
                Logger.d(TAG) {"Work profile disabled"}
            }
        }
    }

    private fun handleProfileEnabled(context: Context) {
        val settings = AppSettings(context)

        Logger.d(TAG) {"Work profile enabled manually"}

        if (!settings.disableVpnOnProfileEnable) {
            Logger.d(TAG) {"disableVpnOnProfileEnable is off, skipping"}
            return
        }

        if (!settings.monitoringEnabled) {
            Logger.d(TAG) {"Monitoring disabled, skipping"}
            return
        }

        if (!VpnUtils.isMonitoredVpnActive(context, settings)) {
            Logger.d(TAG) {"No monitored VPN active, nothing to do"}
            return
        }

        Logger.d(TAG) {"Monitored VPN is active, disabling work profile"}
        val workProfileManager = WorkProfileManager(context)
        val success = workProfileManager.disableWorkProfile()
        Logger.d(TAG) {"Disable work profile result: $success"}

        if (success) {
            vibrate(context)
            playSound(context)
            showNotification(context)
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (e: Exception) {
            Logger.w(TAG) {"Vibration failed: ${e.message}"}
        }
    }

    private fun playSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Logger.w(TAG) {"Sound playback failed: ${e.message}"}
        }
    }

    private fun showNotification(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        // Создать канал с фиксированным ID
        val channel = NotificationChannel(
            CHANNEL_ID_ALERT,
            context.getString(R.string.notification_channel_alert_name),
            // IMPORTANCE_HIGH запрашивается, но на HyperOS FocusPlugin может понизить до SILENT
            // Мгновенная обратная связь обеспечивается вибрацией и звуком выше
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(false)
            enableVibration(false) // вибрация уже сделана отдельно
            setSound(null, null)   // звук уже сделан отдельно
        }
        notificationManager.createNotificationChannel(channel)

        val notification = Notification.Builder(context, CHANNEL_ID_ALERT)
            .setContentTitle(context.getString(R.string.notification_blocked_title))
            .setContentText(context.getString(R.string.notification_blocked_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BLOCKED, notification)
        Logger.d(TAG) {"Notification sent"}
    }
}
