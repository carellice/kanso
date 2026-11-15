package app.kanso.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Telephony
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String, val category: Category)

/** Categorie che Kanso considera a rischio distrazione: l'attesa prima dell'apertura è più lunga. */
val DistractingCategories = setOf(Category.SOCIAL, Category.VIDEO, Category.GIOCHI, Category.SHOPPING)

class AppsRepository(private val context: Context) {
    private val pm = context.packageManager
    private val icons = LruCache<String, ImageBitmap>(200)

    suspend fun load(): List<AppEntry> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map {
                val label = it.loadLabel(pm).toString()
                AppEntry(it.packageName, label, categorize(it.packageName, label, it.applicationInfo.category))
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** Telefono, Messaggi, Fotocamera, Galleria, Mappe: le app predefinite del telefono, se presenti. */
    fun essentials(): List<String> = listOfNotNull(
        resolve(Intent(Intent.ACTION_DIAL)),
        Telephony.Sms.getDefaultSmsPackage(context) ?: resolve(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"))),
        resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) ?: resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)),
        resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS))
            ?: resolve(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0"))),
    ).distinct()

    // "android" è il selettore di sistema: vuol dire che non c'è un'app predefinita.
    private fun resolve(intent: Intent): String? =
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.takeIf { it != "android" }

    fun launchIntent(pkg: String): Intent? = pm.getLaunchIntentForPackage(pkg)

    suspend fun icon(pkg: String): ImageBitmap? {
        icons.get(pkg)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { pm.getApplicationIcon(pkg).toBitmap(168, 168).asImageBitmap() }.getOrNull()?.also { icons.put(pkg, it) }
        }
    }

    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)
}
