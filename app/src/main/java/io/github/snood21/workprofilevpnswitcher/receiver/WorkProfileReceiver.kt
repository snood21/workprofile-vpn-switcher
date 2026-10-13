package io.github.snood21.workprofilevpnswitcher.receiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.RingtoneManager
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils
import io.github.snood21.workprofilevpnswitcher.util.VpnUtils
import io.github.snood21.workprofilevpnswitcher.util.WorkProfileManager
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.service.StealthVpnService
import io.github.snood21.workprofilevpnswitcher.util.Logger

/**
 * Отслеживает ручное включение рабочего профиля.
 *
 * Если пользователь включил рабочий профиль вручную, а в это время активен
 * VPN из списка — выполняется одно из двух действий, в зависимости от
 * AppSettings.disableVpnOnProfileEnable:
 * - включена: отключить сам VPN через заглушку (StealthVpnService), профиль
 *   остаётся включённым. Если заглушка не сработала (establish() вернул null
 *   без исключения — возможно, например, если VPN уже был подготовлен другим
 *   приложением на момент вызова), выполняется откат к старому поведению —
 *   отключение профиля;
 * - выключена: отключить рабочий профиль обратно (старое поведение).
 *
 * Обе ветки активны только если AppSettings.monitorProfileEnable включена.
 *
 * Регистрация: динамическая, из VpnMonitorService.startMonitoring() (снимается в
 * stopMonitoring()). В манифесте ресивер не объявлен: по документации Android
 * ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE отправляются только зарегистрированным
 * через Context.registerReceiver() получателям, а не манифестным. Манифестное
 * объявление срабатывало не на всех прошивках.
 */
class WorkProfileReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkProfileReceiver"
        private const val CHANNEL_ID_ALERT = "vpn_block_alert_channel"
        private const val NOTIFICATION_ID_BLOCKED = 1002
        private const val STEALTH_VPN_RESULT_TIMEOUT_MS = 5000L
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

        if (!settings.monitoringEnabled) {
            Logger.d(TAG) {"Monitoring disabled, skipping"}
            return
        }

        if (!settings.monitorProfileEnable) {
            Logger.d(TAG) {"Profile monitoring disabled, skipping"}
            return
        }

        if (!VpnUtils.isMonitoredVpnActive(context, settings)) {
            Logger.d(TAG) {"No monitored VPN active, nothing to do"}
            return
        }

        if (settings.disableVpnOnProfileEnable) {
            Logger.d(TAG) {"Monitored VPN is active, attempting to disable VPN via stealth VPN"}
            // BroadcastReceiver перестаёт быть валидным сразу после возврата
            // из onReceive() — система может убить процесс до того, как придёт
            // асинхронный результат от StealthVpnService. goAsync() продлевает
            // жизнь ресивера до явного вызова pendingResult.finish().
            val pendingResult = goAsync()
            val handler = Handler(Looper.getMainLooper())
            var finished = false

            fun finishOnce(action: () -> Unit) {
                if (finished) return
                finished = true
                action()
                pendingResult.finish()
            }

            val resultReceiver = object : ResultReceiver(handler) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    finishOnce {
                        if (resultCode == StealthVpnService.RESULT_CODE_ENGAGED) {
                            Logger.d(TAG) {"Stealth VPN succeeded"}
                            vibrate(context)
                            playSound(context)
                            showNotification(
                                context,
                                R.string.notification_vpn_disabled_title,
                                R.string.notification_vpn_disabled_text
                            )
                        } else {
                            Logger.w(TAG) {"Stealth VPN failed - falling back to disabling work profile"}
                            disableWorkProfileAndNotify(context)
                        }
                    }
                }
            }

            // Защита от случая, если StealthVpnService никогда не ответит
            // (например, если процесс сервиса был убит системой) — без этого
            // pendingResult.finish() не вызвался бы вовсе.
            handler.postDelayed({
                finishOnce {
                    Logger.w(TAG) {"Stealth VPN result timed out - falling back to disabling work profile"}
                    disableWorkProfileAndNotify(context)
                }
            }, STEALTH_VPN_RESULT_TIMEOUT_MS)

            // startService может бросить исключение (например, запрет фонового
            // запуска сервиса). Без перехвата ресивер упал бы, не выполнив откат.
            try {
                context.startService(StealthVpnService.intent(context, resultReceiver))
            } catch (e: Exception) {
                Logger.w(TAG) {"Failed to start StealthVpnService: ${e.message}"}
                finishOnce {
                    Logger.w(TAG) {"Falling back to disabling work profile"}
                    disableWorkProfileAndNotify(context)
                }
            }
            return
        }

        Logger.d(TAG) {"Monitored VPN is active, disabling work profile"}
        disableWorkProfileAndNotify(context)
    }

    private fun disableWorkProfileAndNotify(context: Context) {
        val workProfileManager = WorkProfileManager(context)
        val success = workProfileManager.disableWorkProfile()
        Logger.d(TAG) {"Disable work profile result: $success"}

        if (success) {
            vibrate(context)
            playSound(context)
            showNotification(
                context,
                R.string.notification_blocked_title,
                R.string.notification_blocked_text
            )
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

    private fun showNotification(context: Context, titleRes: Int, textRes: Int) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // Строки — на языке приложения (в ресивере контекст использует системную локаль)
        val localized = LanguageUtils.localizedContext(context)

        // Создать канал с фиксированным ID
        val channel = NotificationChannel(
            CHANNEL_ID_ALERT,
            localized.getString(R.string.notification_channel_alert_name),
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
            .setContentTitle(localized.getString(titleRes))
            .setContentText(localized.getString(textRes))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setTimeoutAfter(5000)
            .build()

        notificationManager.notify(NOTIFICATION_ID_BLOCKED, notification)
        Logger.d(TAG) {"Notification sent"}
    }
}
