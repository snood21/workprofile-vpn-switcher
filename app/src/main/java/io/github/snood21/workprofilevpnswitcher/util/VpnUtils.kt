package io.github.snood21.workprofilevpnswitcher.util
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Утилиты для определения активных VPN-подключений.
 * Используется из WorkProfileReceiver для обратной логики.
 *
 * Логика определения пакета (в порядке приоритета):
 * 1. ownerUid — точная идентификация
 * 2. NetworkInfo.extraInfo — "VPN:<package>" (deprecated, работает на HyperOS)
 * 3. Fallback — зависит от режима списка (shouldMonitorUnknownVpn)
 *
 * Логика фильтрации определяется режимом списка в AppSettings:
 * - allowlist: мониторить только пакеты из списка
 * - denylist: мониторить всё кроме пакетов из списка
 */
object VpnUtils {
    private const val TAG = "VpnUtils"
    fun isMonitoredVpnActive(context: Context, settings: AppSettings): Boolean {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            // resolveVpnPackage содержит всю логику — ownerUid, extraInfo, unknown
            if (resolveVpnPackage(network, caps, connectivityManager, context, settings) != null) return true
        }
        return false
    }
    fun resolveVpnPackage(
        network: Network,
        caps: NetworkCapabilities,
        connectivityManager: ConnectivityManager,
        context: Context,
        settings: AppSettings
    ): String? {
        val ownerUid = caps.ownerUid
        if (ownerUid >= 0) {
            val candidate = context.packageManager.getPackagesForUid(ownerUid)?.firstOrNull()
            return if (candidate != null && settings.isVpnPackageMonitored(candidate)) {
                Logger.d(TAG) {"VPN identified by ownerUid: $candidate"}
                candidate
            } else {
                Logger.d(TAG) {"VPN owner $candidate not monitored"}
                null
            }
        }
        val packageFromExtra = resolveVpnPackageFromExtraInfo(connectivityManager, network)
        if (packageFromExtra != null) {
            return if (settings.isVpnPackageMonitored(packageFromExtra)) {
                Logger.d(TAG) {"VPN identified by extraInfo: $packageFromExtra"}
                packageFromExtra
            } else {
                Logger.d(TAG) {"VPN $packageFromExtra not monitored"}
                null
            }
        }
        return if (settings.shouldMonitorUnknownVpn()) {
            Logger.d(TAG) {"ownerUid and extraInfo unavailable, monitoring unknown VPN"}
            "unknown"
        } else {
            Logger.d(TAG) {"ownerUid and extraInfo unavailable, not monitoring (allowlist mode)"}
            null
        }
    }

    fun resolveVpnPackageFromExtraInfo(connectivityManager: ConnectivityManager, network: Network): String? {
        @Suppress("DEPRECATION")
        val extraInfo = connectivityManager.getNetworkInfo(network)?.extraInfo
        val packageFromExtra = extraInfo
            ?.takeIf { it.startsWith("VPN:") }
            ?.removePrefix("VPN:")
            ?.takeIf { it.isNotEmpty() }
        Logger.d(TAG) {"extraInfo=$extraInfo, packageFromExtra=$packageFromExtra"}
        return packageFromExtra
    }
}
