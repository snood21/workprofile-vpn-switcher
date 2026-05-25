package io.github.snood21.workprofilevpnswitcher.service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.ui.MainActivity
import io.github.snood21.workprofilevpnswitcher.util.AppSettings
import io.github.snood21.workprofilevpnswitcher.util.AppSettings.Companion.DEFAULT_POLL_INTERVAL_MS
import io.github.snood21.workprofilevpnswitcher.util.Logger
import io.github.snood21.workprofilevpnswitcher.util.VpnUtils
import io.github.snood21.workprofilevpnswitcher.util.WorkProfileManager

/**
 * Фоновый сервис мониторинга VPN-подключений.
 *
 * Логика определения VPN-приложения (в порядке приоритета):
 * 1. ownerUid — точная идентификация
 * 2. NetworkInfo.extraInfo — "VPN:<package>" (deprecated, работает на HyperOS)
 *
 * Polling запускается только если включён в расширенных настройках.
 */
class VpnMonitorService : Service() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            checkVpnState()
            handler.postDelayed(this, settings.pollIntervalMs)
        }
    }
    companion object {
        private const val TAG = "VpnMonitorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "vpn_monitor_channel"
        private const val REQUEST_CODE_OPEN_APP = 1101
        private const val REQUEST_CODE_STOP = 1102
        const val ACTION_START = "io.github.snood21.workprofilevpnswitcher.START"
        const val ACTION_STOP = "io.github.snood21.workprofilevpnswitcher.STOP"
        fun startIntent(context: Context) =
            Intent(context, VpnMonitorService::class.java).apply { action = ACTION_START }
        fun stopIntent(context: Context) =
            Intent(context, VpnMonitorService::class.java).apply { action = ACTION_STOP }
    }
    private lateinit var settings: AppSettings
    private lateinit var workProfileManager: WorkProfileManager
    private lateinit var connectivityManager: ConnectivityManager
    private val activeVpnNetworks = mutableMapOf<Network, String>()
    private var monitoringStarted = false
    private var lastWorkProfileBlockTime = 0L
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            handler.post { handleVpnAppeared(network, networkCapabilities) }
        }
        override fun onLost(network: Network) {
            handler.post { handleVpnLost(network) }
        }
    }
    // --- Lifecycle ---
    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        workProfileManager = WorkProfileManager(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        createNotificationChannel()
        Logger.d(TAG) {"Service created"}
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Logger.d(TAG) { "Stop command received" }
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                Logger.d(TAG) { "Start command received" }
                startForeground(NOTIFICATION_ID, buildNotification())
                startMonitoring()
                return START_STICKY
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        stopMonitoring()
        Logger.d(TAG) {"Service destroyed"}
        super.onDestroy()
    }
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.d(TAG) { "Task removed" }
        super.onTaskRemoved(rootIntent)
    }
    override fun onTrimMemory(level: Int) {
        Logger.d(TAG) { "Trim memory: $level" }
        super.onTrimMemory(level)
    }
    // --- Мониторинг ---
    private fun startMonitoring() {
        if (monitoringStarted) {
            handler.removeCallbacks(pollRunnable)

            if (settings.pollingEnabled) {
                handler.post(pollRunnable)
            } else {
                handler.post { checkVpnState() }
            }
            return
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)
        monitoringStarted = true

        if (settings.pollingEnabled) {
            handler.post(pollRunnable)
        } else {
            handler.post { checkVpnState() }
        }
    }
    private fun stopMonitoring() {
        handler.removeCallbacks(pollRunnable)

        if (monitoringStarted) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}

            monitoringStarted = false
        }
        activeVpnNetworks.clear()
    }
    // --- Обработка событий VPN ---
    private fun handleVpnAppeared(network: Network, caps: NetworkCapabilities) {
        if (activeVpnNetworks.containsKey(network)) return

        val vpnPackage = VpnUtils.resolveVpnPackage(network, caps, this.connectivityManager, this, this.settings) ?: run {
            Logger.d(TAG) { "VPN not monitored, ignoring" }
            return
        }
        Logger.d(TAG) { "Resolved VPN package: $vpnPackage" }

        // Добавляем в map сразу — до любых проверок состояния.
        // Это предотвращает повторный вход для той же сети.
        activeVpnNetworks[network] = vpnPackage

        val isActive = workProfileManager.isWorkProfileActive()
        if (isActive == null) {
            Logger.w(TAG) { "Work profile not found, skipping" }
            return
        }

        val firstVpn = activeVpnNetworks.size == 1  // мы только что добавили первый
        if (firstVpn && settings.restoreProfileState) {
            settings.saveProfileState(isActive)
            Logger.d(TAG) { "Saved profile state: wasActive=$isActive" }
        }

        if (isActive) {
            val success = workProfileManager.disableWorkProfile()
            Logger.d(TAG) { "Disable work profile result: $success" }
            updateNotification(vpnActive = true)
        } else {
            Logger.d(TAG) { "Work profile was already inactive, nothing to do" }
        }
    }
    private fun handleVpnLost(network: Network) {
        val vpnPackage = activeVpnNetworks.remove(network) ?: return
        if (activeVpnNetworks.isNotEmpty()) {
            Logger.d(TAG) {"Other monitored VPNs still active, skip restore"}
            return
        }
        Logger.d(TAG) {"Monitored VPN disconnected: $vpnPackage"}
        if (!settings.restoreProfileState) {
            Logger.d(TAG) {"Restore disabled — leaving work profile as is"}
            updateNotification(vpnActive = false)
            return
        }
        val savedState = settings.getSavedProfileState()
        when {
            savedState == null -> Logger.d(TAG) {"No saved state found, nothing to restore"}
            savedState -> {
                val success = workProfileManager.enableWorkProfile()
                Logger.d(TAG) {"Restore work profile result: $success"}
            }
            else -> Logger.d(TAG) {"Saved state was inactive, not restoring"}
        }
        settings.clearSavedProfileState()
        updateNotification(vpnActive = false)
    }
    private fun checkVpnState() {
        //Альтернативы для получения всех текущих сетей без callback нет в публичном API
        @Suppress("DEPRECATION")
        val currentNetworks = connectivityManager.allNetworks.toSet()
        val currentVpnNetworks = currentNetworks.filter { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }.toSet()
        for (network in currentVpnNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            handleVpnAppeared(network, caps)
        }
        val lostNetworks = activeVpnNetworks.keys.filter { it !in currentNetworks }
        for (network in lostNetworks) {
            Logger.d(TAG) {"Polling detected lost network: $network"}
            handleVpnLost(network)
        }
        if (activeVpnNetworks.isNotEmpty()) {
            checkAndDisableWorkProfileIfNeeded()
        }
    }
    private fun checkAndDisableWorkProfileIfNeeded() {
        if (!settings.disableVpnOnProfileEnable) return
        val isActive = workProfileManager.isWorkProfileActive() ?: return
        if (!isActive) return
        val now = System.currentTimeMillis()
        val cooldown = if (settings.pollingEnabled) settings.pollIntervalMs else DEFAULT_POLL_INTERVAL_MS
        if (now - lastWorkProfileBlockTime < cooldown) return
        lastWorkProfileBlockTime = now
        Logger.d(TAG) {"Work profile is active while monitored VPN running — disabling"}
        val success = workProfileManager.disableWorkProfile()
        Logger.d(TAG) {"Auto-disable work profile result: $success"}
        if (success) {
            try {
                val vibrator = getSystemService(Vibrator::class.java)
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) {
                Logger.w(TAG) {"Vibration failed: ${e.message}"}
            }
        }
    }
    // --- Уведомление ---
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
    private fun buildNotification(vpnActive: Boolean = false): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val statusText = if (vpnActive) getString(R.string.notification_vpn_active)
        else getString(R.string.notification_monitoring)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stopIntent).build())
            .build()
    }
    private fun updateNotification(vpnActive: Boolean = false) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(vpnActive))
    }
}
