package io.github.snood21.workprofilevpnswitcher.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.ResultReceiver
import androidx.core.content.IntentCompat
import io.github.snood21.workprofilevpnswitcher.util.Logger

/**
 * VPN-заглушка для принудительного отключения активного VPN-клиента.
 *
 * Android допускает только один активный VpnService в системе одновременно —
 * установка нового VPN-туннеля автоматически вытесняет любой уже работающий,
 * независимо от того, какое приложение его создало. Сервис использует это
 * системное ограничение: устанавливает временный туннель и сразу же
 * его закрывает, не передавая никакого трафика.
 *
 * Требует предварительного согласия пользователя через VpnService.prepare()
 * (запрашивается один раз, см. MainActivity). Без этого establish() бросит
 * SecurityException.
 *
 * ВНИМАНИЕ: если у вытесняемого VPN-клиента включён Always-on VPN в системных
 * настройках Android, система может переподключить его автоматически —
 * приём в этом случае не даёт гарантированного результата.
 *
 * Собственный туннель этого сервиса виден системе как обычная VPN-сеть
 * с ownerUid, равным UID этого приложения — вызывающая сторона должна
 * исключать этот UID из мониторинга (см. VpnUtils), иначе кратковременное
 * появление/исчезновение этой сети будет ошибочно обработано как появление
 * стороннего VPN.
 *
 * Результат попытки (успех/неудача) передаётся вызывающей стороне через
 * необязательный ResultReceiver (см. intent()) — establish() может тихо
 * вернуть null без исключения (например, если VPN уже подготовлен другим
 * приложением на момент вызова), поэтому запуск сервиса не гарантирует
 * успешное отключение стороннего VPN.
 */
class StealthVpnService : VpnService() {

    companion object {
        private const val TAG = "StealthVpnService"
        const val ACTION_ENGAGE = "io.github.snood21.workprofilevpnswitcher.STEALTH_ENGAGE"
        const val EXTRA_RESULT_RECEIVER = "result_receiver"
        const val RESULT_CODE_ENGAGED = 0
        const val RESULT_CODE_FAILED = 1

        fun intent(context: Context, resultReceiver: ResultReceiver? = null) =
            Intent(context, StealthVpnService::class.java).apply {
                action = ACTION_ENGAGE
                if (resultReceiver != null) {
                    putExtra(EXTRA_RESULT_RECEIVER, resultReceiver)
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG) { "Engaging stealth VPN to displace active VPN client" }
        val resultReceiver = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_RESULT_RECEIVER, ResultReceiver::class.java)
        }
        engageAndRelease(resultReceiver)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Logger.d(TAG) { "Service destroyed" }
        super.onDestroy()
    }

    /**
     * Устанавливает и немедленно закрывает VPN-туннель.
     *
     * Само создание туннеля (establish()) — это точка, где система
     * отзывает VPN-туннель у ранее активного клиента. Дальнейшая жизнь
     * этого туннеля не нужна, поэтому он закрывается сразу же после
     * установки, без чтения/записи трафика.
     */
    private fun engageAndRelease(resultReceiver: ResultReceiver?) {
        val prepareIntent = prepare(this)
        Logger.d(TAG) { "VpnService.prepare() returned: ${if (prepareIntent == null) "null (already prepared)" else "non-null (consent needed)"}" }

        val fd = try {
            Builder()
                .addAddress("192.0.2.1", 32)
                .addRoute("192.0.2.1", 32)
                .setSession("WorkProfileVpnSwitcher-Stealth")
                .establish()
        } catch (e: SecurityException) {
            // Пользователь не давал согласие VpnService.prepare(), либо оно
            // было отозвано — заглушка не может быть установлена.
            Logger.e(TAG, "establish() failed — VPN permission not granted", e)
            null
        } catch (e: IllegalStateException) {
            Logger.e(TAG, "establish() failed — VPN already prepared by another app in this process", e)
            null
        }

        if (fd == null) {
            Logger.w(TAG) { "Stealth VPN was not established" }
            resultReceiver?.send(RESULT_CODE_FAILED, Bundle())
            stopSelf()
            return
        }

        try {
            fd.close()
            Logger.d(TAG) { "Stealth VPN established and released" }
            resultReceiver?.send(RESULT_CODE_ENGAGED, Bundle())
        } catch (e: Exception) {
            Logger.w(TAG) { "Error closing stealth VPN descriptor: ${e.message}" }
            resultReceiver?.send(RESULT_CODE_FAILED, Bundle())
        } finally {
            stopSelf()
        }
    }
}
