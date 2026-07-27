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
     *
     * ВНИМАНИЕ: текущая логика "первый чужой профиль" не эквивалентна
     * "рабочий профиль" — на Android 15+ (private space), при наличии
     * второго пользователя, guest-профиля или производительских clone-профилей
     * может вернуть неверный handle.
     *
     * Точное определение через UserManager.isManagedProfile для произвольного
     * UserHandle не решено: скрытая версия isManagedProfile(int userId) требует
     * MANAGE_USERS (недоступно обычным приложениям); попытка получить контекст
     * через Context.createContextAsUser не компилируется — это non-SDK/скрытый
     * интерфейс, недоступный из публичного Android SDK (проверено эмпирически —
     * "Unresolved reference" при компиляции с compileSdk 37).
     * Единственный официально доступный публичный метод без параметра —
     * UserManager.isManagedProfile() без аргументов (API 30+) — проверяет
     * только пользователя ТЕКУЩЕГО контекста, не годится для проверки
     * произвольного UserHandle из списка.
     * Требуется другая стратегия определения либо явное документирование
     * ограничения (см. README).
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
        return !userManager.isQuietModeEnabled(handle)
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
            val requestAccepted = userManager.requestQuietModeEnabled(true, handle)
            if (requestAccepted) {
                Logger.d(TAG) {"Work profile disabled"}
            } else {
                Logger.w(TAG) {"disableWorkProfile: system declined the request (requestQuietModeEnabled returned false)"}
            }
            requestAccepted
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
            val requestAccepted = userManager.requestQuietModeEnabled(false, handle)
            if (requestAccepted) {
                Logger.d(TAG) {"Work profile enabled"}
            } else {
                Logger.w(TAG) {"enableWorkProfile: system declined the request (requestQuietModeEnabled returned false) — may require user credential confirmation"}
            }
            requestAccepted
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
