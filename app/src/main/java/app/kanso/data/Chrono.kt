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

    /** Riallinea con tutte le notifiche presenti nel pannello. */
    fun sync(c: Context, all: Array<StatusBarNotification>) {
        active = all.mapNotNull { runCatching { read(c, it) }.getOrNull() }.sortedBy { it.key }
    }

    fun remove(key: String) { active = active.filter { it.key != key } }
    fun clear() { active = emptyList() }

    private fun read(c: Context, sbn: StatusBarNotification): ChronoInfo? {
        // shortcut: riconosce l'Orologio dal nome del pacchetto (Google, AOSP, Samsung, Xiaomi), altre app timer no.
        if (!sbn.packageName.contains("clock", ignoreCase = true)) return null
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val texts = TEXTS.mapNotNull { n.extras.getCharSequence(it)?.toString()?.takeIf(String::isNotBlank) } +
            n.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }
        val all = (texts + n.channelId.orEmpty()).joinToString(" ")
        // shortcut: sveglie e modalità notte si riconoscono dalle parole italiane e inglesi.
        if (Skip.containsMatchIn(all)) return null
        val stopwatchHint = all.contains("cronometro", true) || all.contains("stopwatch", true) || n.category == Notification.CATEGORY_STOPWATCH
        // Chi usa il cronometro standard della notifica (anche i Live Update di Android 16).
        if (n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) {
            return ChronoInfo(sbn.key, sbn.packageName, n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN), n.`when`, null)
        }
        // Google Orologio e AOSP disegnano un Chronometer dentro una vista personalizzata: la gonfio e lo leggo.
        val views = inflate(c, sbn)
        views.firstNotNullOfOrNull { findChrono(it) }?.let { chrono ->
            val base = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - chrono.base)
            // In corso c'è l'azione «Pausa» (Pause, Pausa…); in pausa no. Un timer già scaduto continua a contare in negativo.
            val running = pausable(n) || (chrono.isCountDown && base < System.currentTimeMillis())
            return ChronoInfo(sbn.key, sbn.packageName, chrono.isCountDown, base, if (running) null else chrono.text.toString())
        }
        // Tempo scritto nel testo della notifica o in una scritta della vista personalizzata.
        val time = (texts + views.flatMap { viewTexts(it) }).firstNotNullOfOrNull { TIME.find(it)?.value }
        if (!sbn.isOngoing && time == null) return null
        // Ultima risorsa: una notifica in corso dell'Orologio è un timer o un cronometro; si mostra con il suo titolo.
        val shown = time ?: texts.firstOrNull()?.take(24) ?: if (stopwatchHint) "Cronometro" else "Timer"
        return ChronoInfo(sbn.key, sbn.packageName, !stopwatchHint, 0, shown)
    }

    private val Skip = Regex("svegli|alarm|dormi|bedtime|riposo|sleep", RegexOption.IGNORE_CASE)

    /** Le viste personalizzate della notifica, gonfiate con il contesto di Kanso o, se non basta, con quello dell'Orologio. */
    private fun inflate(c: Context, sbn: StatusBarNotification): List<View> {
        val n = sbn.notification
        val remote = listOfNotNull(n.contentView, n.bigContentView, n.headsUpContentView)
        if (remote.isEmpty()) return emptyList()
        val contexts = listOfNotNull(
            c,
            android.view.ContextThemeWrapper(c, android.R.style.Theme_DeviceDefault),
            runCatching { c.createPackageContext(sbn.packageName, Context.CONTEXT_RESTRICTED) }.getOrNull(),
        )
        return remote.mapNotNull { rv -> contexts.firstNotNullOfOrNull { ctx -> runCatching { rv.apply(ctx, null) }.getOrNull() } }
    }

    private fun viewTexts(v: View): List<String> = when (v) {
        is android.widget.TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { viewTexts(v.getChildAt(it)) }
        else -> emptyList()
    }

    /** Cosa Kanso vede nelle notifiche dell'Orologio: per capire perché un timer non compare. */
    fun describe(c: Context, all: Array<StatusBarNotification>?): String {
        if (all == null) return "Il servizio delle notifiche non è collegato. Riattiva l'accesso alle notifiche per Kanso."
        val clocks = all.filter { it.packageName.contains("clock", ignoreCase = true) }
        if (clocks.isEmpty()) return "Nessuna notifica dell'Orologio nel pannello (${all.size} notifiche in tutto)."
        return clocks.joinToString("\n\n") { sbn ->
            val n = sbn.notification
            val extras = n.extras.keySet().sorted().joinToString("\n") { k -> "  $k = ${n.extras.get(k)?.toString()?.take(80)}" }
            val inflated = inflate(c, sbn)
            val errors = listOfNotNull(n.contentView, n.bigContentView, n.headsUpContentView).mapNotNull { rv ->
                runCatching { rv.apply(c, null); null }.getOrElse { "${it.javaClass.simpleName}: ${it.message?.take(80)}" }
            }
            buildString {
                appendLine("${sbn.packageName} · ongoing=${sbn.isOngoing} · categoria=${n.category} · canale=${n.channelId}")
                appendLine("flags=0x${Integer.toHexString(n.flags)} · when=${n.`when`} · azioni=${n.actions.orEmpty().joinToString { it.title }}")
                appendLine("viste: content=${n.contentView != null} big=${n.bigContentView != null} headsUp=${n.headsUpContentView != null} · gonfiate=${inflated.size}")
                if (errors.isNotEmpty()) appendLine("errori: ${errors.joinToString(" | ")}")
                appendLine("Chronometer=${inflated.firstNotNullOfOrNull { findChrono(it) } != null} · testi=${inflated.flatMap { viewTexts(it) }.take(6)}")
                appendLine("letto come: ${runCatching { read(c, sbn) }.getOrElse { it.toString() }}")
                append(extras)
            }
        }
    }

    private val TEXTS = listOf(
        Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
    )

    private val TIME = Regex("""\b\d{1,2}(:\d{2}){1,2}\b""")

    private fun pausable(n: Notification) = n.actions.orEmpty().any { it.title?.contains("pau", ignoreCase = true) == true }

    private fun findChrono(v: View): Chronometer? = when (v) {
        is Chronometer -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findChrono(v.getChildAt(it)) }
        else -> null
    }
}
