package io.github.snood21.workprofilevpnswitcher.util
import android.content.Context
import android.os.Process
import android.os.UserManager

/**
 * Управление рабочим профилем через UserManager.
 *
 * Требует разрешения android.permission.MODIFY_QUIET_MODE,
 * которое выдаётся через adb:
 *   adb shell pm grant io.github.snood21.workprofilevpnswitcher android.permission.MODIFY_QUIET_MODE
 *
 * API: Android 10+ (getOwnerUid в NetworkCapabilities),
 * requestQuietModeEnabled доступен с Android 9.
 */
class WorkProfileManager(context: Context) {
    companion object {
        private const val TAG = "WorkProfileManager"
    }
    private val userManager = context.getSystemService(UserManager::class.java)
    /**
     * Найти UserHandle рабочего профиля.
     * Возвращает null если рабочего профиля нет.
     */
    private fun getWorkProfileHandle() =
        userManager.userProfiles
            ?.firstOrNull { it != Process.myUserHandle() }
    /**
     * Проверить, активен ли рабочий профиль сейчас.
     * Возвращает null если рабочего профиля нет.
     */
    fun isWorkProfileActive(): Boolean? {
        val handle = getWorkProfileHandle() ?: run {
            Logger.w(TAG) {"Work profile not found"}
            return null
        }
        // isQuietModeEnabled == true означает профиль ВЫКЛЮЧЕН
        return userManager.isQuietModeEnabled(handle) == false
    }
    /**
     * Отключить рабочий профиль.
     * @return true если операция выполнена, false если профиль не найден или уже неактивен
     */
    fun disableWorkProfile(): Boolean {
        val handle = getWorkProfileHandle() ?: run {
            Logger.w(TAG) {"disableWorkProfile: work profile not found"}
            return false
        }
        return try {
            userManager.requestQuietModeEnabled(true, handle)
            Logger.d(TAG) {"Work profile disabled"}
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "disableWorkProfile: missing MODIFY_QUIET_MODE permission", e)
            false
        }
    }
    /**
     * Включить рабочий профиль.
     * @return true если операция выполнена, false если профиль не найден
     */
    fun enableWorkProfile(): Boolean {
        val handle = getWorkProfileHandle() ?: run {
            Logger.w(TAG) {"enableWorkProfile: work profile not found"}
            return false
        }
        return try {
            userManager.requestQuietModeEnabled(false, handle)
            Logger.d(TAG) {"Work profile enabled"}
            true
        } catch (e: SecurityException) {
            Logger.e(TAG, "enableWorkProfile: missing MODIFY_QUIET_MODE permission", e)
            false
        }
    }
    /**
     * Проверить наличие рабочего профиля на устройстве.
     */
    fun hasWorkProfile(): Boolean = getWorkProfileHandle() != null
    /**
     * Проверить наличие разрешения MODIFY_QUIET_MODE.
     * Используется для отображения предупреждения в UI.
     */
    fun hasRequiredPermission(): Boolean {
        val handle = getWorkProfileHandle() ?: return false
        return try {
            userManager.isQuietModeEnabled(handle)
            true
        } catch (e: SecurityException) { false }
    }
}
