package app.kanso.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un'app dello spazio privato: si apre e si disegna passando dal suo profilo, non dal nome del pacchetto. */
class PrivateApp(val info: LauncherActivityInfo) {
    val key = info.componentName.flattenToString()
    val label = info.label.toString()
    var icon: ImageBitmap? = null; private set

    suspend fun loadIcon(): ImageBitmap? = icon ?: withContext(Dispatchers.IO) {
        runCatching { info.getBadgedIcon(0).toBitmap(168, 168).asImageBitmap() }.getOrNull()?.also { icon = it }
    }
}

/**
 * Lo spazio privato di Android 15: il profilo si vede solo se Kanso è l'app Home (permesso ACCESS_HIDDEN_PROFILES).
 * Le sue app si elencano solo quando è sbloccato.
 */
class PrivateSpace(context: Context) {
    private val launcher = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)

    fun profile(): UserHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return runCatching {
            launcher.profiles.firstOrNull { launcher.getLauncherUserInfo(it)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE }
        }.getOrNull()
    }

    fun isLocked(user: UserHandle) = users.isQuietModeEnabled(user)

    suspend fun apps(user: UserHandle): List<PrivateApp> = withContext(Dispatchers.IO) {
        runCatching { launcher.getActivityList(null, user) }.getOrDefault(emptyList())
            .map(::PrivateApp)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Sbloccare chiede a Android il metodo di sblocco dello spazio privato. */
    fun setLocked(user: UserHandle, locked: Boolean) = runCatching { users.requestQuietModeEnabled(locked, user) }.isSuccess

    fun launch(app: PrivateApp) = runCatching {
        launcher.startMainActivity(app.info.componentName, app.info.user, null, null)
    }.isSuccess
}
