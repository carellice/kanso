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
        // Google Orologio su Android recenti: MetricStyle, il tempo sta in «android.metrics».
        metric(sbn, !stopwatchHint)?.let { return it }
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

    /**
     * Legge timer e cronometro da Notification.MetricStyle (Android 17), senza dipendere dalle sue classi:
     * l'SDK usato per compilare non le ha ancora. Si cerca un istante (fine del timer o inizio del cronometro)
     * e un'eventuale durata in pausa tra i valori delle metriche.
     */
    private fun metric(sbn: StatusBarNotification, countDown: Boolean): ChronoInfo? {
        val n = sbn.notification
        val raw = n.extras.get(EXTRA_METRICS) ?: return null
        val values = mutableListOf<Pair<String, Any>>()
        leaves("", raw, values, 0)
        if (values.isEmpty()) return null
        val now = System.currentTimeMillis()
        val up = SystemClock.elapsedRealtime()
        val running = pausable(n)
        val paused = values.firstNotNullOfOrNull { (k, v) -> if (PausedKey.containsMatchIn(k)) millis(v) else null }
        // Un istante vicino a ora: in millisecondi o secondi dal 1970, oppure in tempo dall'accensione.
        val instants = values.mapNotNull { (k, v) ->
            if (PausedKey.containsMatchIn(k)) return@mapNotNull null
            val l = (v as? java.time.Instant)?.toEpochMilli() ?: (v as? Number)?.toLong() ?: return@mapNotNull null
            when {
                kotlin.math.abs(l - now) < MONTH -> l
                kotlin.math.abs(l * 1000 - now) < MONTH -> l * 1000
                (ElapsedKey.containsMatchIn(k) || l > up / 2) && l in 1..(up + MONTH) -> now - (up - l)
                else -> null
            }
        }
        val zero = (if (countDown) instants.maxOrNull() else instants.minOrNull())
        if (running && zero != null) {
            // Il cronometro non conta il tempo passato in pausa.
            val base = if (!countDown && paused != null && paused > 0) zero + paused else zero
            return ChronoInfo(sbn.key, sbn.packageName, countDown, base, null)
        }
        // In pausa: il tempo fermo, se c'è come durata; altrimenti quello che manca o che è passato dall'istante.
        val frozenMs = paused?.takeIf { it > 0 } ?: zero?.let { if (countDown) it - now else now - it } ?: return null
        return ChronoInfo(sbn.key, sbn.packageName, countDown, 0, format(frozenMs))
    }

    private const val EXTRA_METRICS = "android.metrics"
    private const val MONTH = 30L * 86_400_000L
    private val PausedKey = Regex("pause|duration|remaining|elapsedtime", RegexOption.IGNORE_CASE)
    private val ElapsedKey = Regex("elapsed|realtime|zero|base", RegexOption.IGNORE_CASE)

    private fun millis(v: Any): Long? = (v as? java.time.Duration)?.toMillis() ?: (v as? Number)?.toLong()

    fun format(ms: Long): String {
        val s = kotlin.math.abs(ms) / 1000
        val sign = if (ms < 0 && s > 0) "-" else ""
        return sign + if (s >= 3600) "${s / 3600}:${"%02d".format(s / 60 % 60)}:${"%02d".format(s % 60)}" else "${s / 60}:${"%02d".format(s % 60)}"
    }

    /** Tutti i valori dentro bundle, array e oggetti del framework, con il percorso dei nomi. */
    @Suppress("DEPRECATION")
    private fun leaves(path: String, v: Any?, out: MutableList<Pair<String, Any>>, depth: Int) {
        if (v == null || depth > 6) return
        when (v) {
            is android.os.Bundle -> v.keySet().forEach { k -> leaves("$path/$k", runCatching { v.get(k) }.getOrNull(), out, depth + 1) }
            is Array<*> -> v.forEachIndexed { i, x -> leaves("$path[$i]", x, out, depth + 1) }
            is Iterable<*> -> v.forEachIndexed { i, x -> leaves("$path[$i]", x, out, depth + 1) }
            is Number, is Boolean, is CharSequence, is java.time.Instant, is java.time.Duration -> out += path to v
            else -> if (v.javaClass.name.startsWith("android.app.Notification")) {
                // Le classi nuove (es. Notification.Metric) si leggono dai loro getter pubblici.
                v.javaClass.methods.filter { it.parameterCount == 0 && (it.name.startsWith("get") || it.name.startsWith("is")) && it.name != "getClass" }
                    .forEach { m -> leaves("$path/${m.name}", runCatching { m.invoke(v) }.getOrNull(), out, depth + 1) }
            } else out += path to v.toString()
        }
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
                n.extras.get(EXTRA_METRICS)?.let { raw ->
                    val values = mutableListOf<Pair<String, Any>>()
                    runCatching { leaves("", raw, values, 0) }
                    appendLine("metriche (ora=${System.currentTimeMillis()}, accensione=${SystemClock.elapsedRealtime()}):")
                    values.take(40).forEach { (k, v) -> appendLine("  $k = ${v.toString().take(60)} (${v.javaClass.simpleName})") }
                }
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
