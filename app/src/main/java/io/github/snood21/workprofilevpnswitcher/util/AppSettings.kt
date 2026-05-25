package io.github.snood21.workprofilevpnswitcher.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Централизованное хранилище настроек приложения.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "vpn_work_profile_prefs"
        private const val KEY_VPN_PACKAGES = "vpn_packages"
        private const val KEY_RESTORE_PROFILE_STATE = "restore_profile_state"
        private const val KEY_SAVED_PROFILE_STATE = "saved_profile_state"
        private const val KEY_HAS_SAVED_STATE = "has_saved_state"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_DISABLE_VPN_ON_PROFILE_ENABLE = "disable_vpn_on_profile_enable"
        private const val KEY_LIST_MODE_ALLOWLIST = "list_mode_allowlist"
        private const val KEY_POLLING_ENABLED = "polling_enabled"
        private const val KEY_POLL_INTERVAL_MS = "poll_interval_ms"
        private const val KEY_APP_LANGUAGE = "app_language"
        const val DEFAULT_POLL_INTERVAL_MS = 3000L
    }

    // --- VPN-пакеты ---

    fun getVpnPackages(): Set<String> =
        prefs.getStringSet(KEY_VPN_PACKAGES, emptySet())?.toSet() ?: emptySet()

    fun addVpnPackage(packageName: String) {
        prefs.edit { putStringSet(KEY_VPN_PACKAGES, getVpnPackages() + packageName) }
    }

    fun removeVpnPackage(packageName: String) {
        prefs.edit { putStringSet(KEY_VPN_PACKAGES, getVpnPackages() - packageName) }
    }

    /**
     * Проверить, нужно ли мониторить данный пакет с учётом режима списка.
     * allowlist: мониторить только если пакет в списке
     * denylist: мониторить всё кроме пакетов из списка
     */
    fun isVpnPackageMonitored(packageName: String): Boolean {
        val inList = getVpnPackages().contains(packageName)
        return if (listModeAllowlist) inList else !inList
    }

    /**
     * Нужно ли мониторить VPN с неизвестным владельцем.
     * allowlist: нет — неизвестный пакет не в списке
     * denylist: да — неизвестный пакет не исключён
     */
    fun shouldMonitorUnknownVpn(): Boolean = !listModeAllowlist

    // --- Режим списка ---

    var listModeAllowlist: Boolean
        get() = prefs.getBoolean(KEY_LIST_MODE_ALLOWLIST, false)
        set(value) = prefs.edit { putBoolean(KEY_LIST_MODE_ALLOWLIST, value) }

    // --- Восстановление состояния ---

    var restoreProfileState: Boolean
        get() = prefs.getBoolean(KEY_RESTORE_PROFILE_STATE, false)
        set(value) = prefs.edit { putBoolean(KEY_RESTORE_PROFILE_STATE, value) }

    fun saveProfileState(wasActive: Boolean) {
        prefs.edit {
            putBoolean(KEY_SAVED_PROFILE_STATE, wasActive)
            putBoolean(KEY_HAS_SAVED_STATE, true)
        }
    }

    fun getSavedProfileState(): Boolean? {
        if (!prefs.getBoolean(KEY_HAS_SAVED_STATE, false)) return null
        return prefs.getBoolean(KEY_SAVED_PROFILE_STATE, false)
    }

    fun clearSavedProfileState() {
        prefs.edit {
            remove(KEY_SAVED_PROFILE_STATE)
            putBoolean(KEY_HAS_SAVED_STATE, false)
        }
    }

    // --- Мониторинг ---

    var monitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_MONITORING_ENABLED, value) }

    var disableVpnOnProfileEnable: Boolean
        get() = prefs.getBoolean(KEY_DISABLE_VPN_ON_PROFILE_ENABLE, false)
        set(value) = prefs.edit { putBoolean(KEY_DISABLE_VPN_ON_PROFILE_ENABLE, value) }

    // --- Polling ---

    var pollingEnabled: Boolean
        get() = prefs.getBoolean(KEY_POLLING_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_POLLING_ENABLED, value) }

    var pollIntervalMs: Long
        get() = prefs.getLong(KEY_POLL_INTERVAL_MS, DEFAULT_POLL_INTERVAL_MS)
        set(value) = prefs.edit { putLong(KEY_POLL_INTERVAL_MS, value) }

    // --- Язык ---

    var appLanguage: String
        get() = prefs.getString(KEY_APP_LANGUAGE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_APP_LANGUAGE, value) }
}
