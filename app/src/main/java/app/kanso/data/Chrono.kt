package app.kanso.data

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Un timer o cronometro attivo nell'app Orologio.
 * [base]: per il timer l'istante in cui finisce, per il cronometro quello in cui è partito (ora di sistema).
 * Se è in pausa, [frozen] è il tempo mostrato dall'Orologio.
 */
data class ChronoInfo(val key: String, val pkg: String, val countDown: Boolean, val base: Long, val frozen: String?)

/** Timer e cronometri letti dalle notifiche dell'Orologio. Il servizio delle notifiche vive nello stesso processo. */
object Chrono {
    var active by mutableStateOf<List<ChronoInfo>>(emptyList()); private set

    fun update(c: Context, sbn: StatusBarNotification) {
        val info = runCatching { read(c, sbn) }.getOrNull()
        active = (active.filter { it.key != sbn.key } + listOfNotNull(info)).sortedBy { it.key }
    }

    fun remove(key: String) { active = active.filter { it.key != key } }
    fun clear() { active = emptyList() }

    private fun read(c: Context, sbn: StatusBarNotification): ChronoInfo? {
        // shortcut: riconosce l'Orologio dal nome del pacchetto (Google, AOSP, Samsung, Xiaomi), altre app timer no.
        if (!sbn.packageName.contains("clock", ignoreCase = true)) return null
        val n = sbn.notification
        // Chi usa il cronometro standard della notifica.
        if (n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
            return ChronoInfo(sbn.key, sbn.packageName, n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), n.`when`, null)
        }
        // Google Orologio e AOSP disegnano un Chronometer dentro una vista personalizzata: la gonfio e lo leggo.
        val views = n.contentView ?: n.bigContentView ?: return null
        val chrono = find(views.apply(c, null)) ?: return null
        val base = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - chrono.base)
        // In corso c'è l'azione «Pausa» (Pause, Pausa…); in pausa no. Un timer già scaduto continua a contare in negativo.
        val running = n.actions.orEmpty().any { it.title?.contains("pau", ignoreCase = true) == true } ||
            (chrono.isCountDown && base < System.currentTimeMillis())
        return ChronoInfo(sbn.key, sbn.packageName, chrono.isCountDown, base, if (running) null else chrono.text.toString())
    }

    private fun find(v: View): Chronometer? = when (v) {
        is Chronometer -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
        else -> null
    }
}
