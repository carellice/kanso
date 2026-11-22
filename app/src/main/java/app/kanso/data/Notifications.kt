package app.kanso.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** [key]: la notifica di Android da cui viene; quando sparisce dal pannello sparisce anche dal riassunto. */
data class Note(val time: Long, val app: String, val title: String, val text: String, val pkg: String = "", val key: String = "")

/**
 * Tiene una copia locale delle notifiche ancora nel pannello (al massimo 24 ore), solo per il riassunto.
 * Non le cancella, non le nasconde e non risponde: la gestione di Android resta intatta.
 */
class NotificationCollector : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        Chrono.sync(this, active)
        recount(active)
        // Quelle tolte mentre Kanso non ascoltava escono dal riassunto.
        NoteStore.keep(this, active.map { it.key }.toSet())
    }

    override fun onListenerDisconnected() {
        instance = null
        Chrono.clear()
        unread = emptyMap(); playing = emptySet()
        // Android a volte stacca il servizio (dopo un aggiornamento, per memoria): si chiede subito di ricollegarlo.
        rebind(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Chrono.remove(sbn.key)
        NoteStore.remove(this, sbn.key)
        runCatching { recount(activeNotifications) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Chrono.update(this, sbn)
        toNote(this, sbn, runCatching { currentRanking }.getOrNull())?.let { NoteStore.add(this, it) }
        runCatching { recount(activeNotifications) }
    }

    companion object {
        @Volatile private var instance: NotificationCollector? = null

        /** Notifiche nel pannello per app (senza quelle in corso e i riepiloghi di gruppo): i numeri della modalità classica. */
        var unread by mutableStateOf<Map<String, Int>>(emptyMap()); private set
        /** App che stanno suonando (Chiarella): la notifica offre "Pausa". Il pulsante in Home si accende. */
        var playing by mutableStateOf<Set<String>>(emptySet()); private set

        private fun recount(active: Array<StatusBarNotification>) {
            unread = active.filter { !it.isOngoing && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .groupingBy { it.packageName }.eachCount()
            // La notifica resta anche in pausa: conta solo se offre di mettere in pausa.
            playing = active.filter { n -> n.notification.actions.orEmpty().any { it.title?.toString()?.lowercase() in setOf("pausa", "pause") } }
                .map { it.packageName }.toSet()
        }

        /** Le notifiche ora nel pannello; null se il servizio non è collegato. */
        fun active(): Array<StatusBarNotification>? = instance?.let { runCatching { it.activeNotifications }.getOrNull() }

        /** Le notifiche del pannello pronte per il riassunto; null se il servizio non è collegato. */
        fun activeNotes(c: Context): List<Note>? {
            val service = instance ?: return null
            val all = runCatching { service.activeNotifications }.getOrNull() ?: return null
            val ranking = runCatching { service.currentRanking }.getOrNull()
            return all.mapNotNull { toNote(c, it, ranking) }.sortedBy { it.time }
        }

        fun rebind(c: Context) = runCatching { requestRebind(ComponentName(c, NotificationCollector::class.java)) }

        /** La notifica in forma leggibile per il riassunto; null per quelle in corso, di riepilogo o vuote. */
        fun toNote(c: Context, sbn: StatusBarNotification, ranking: RankingMap?): Note? {
            val n = sbn.notification
            if (sbn.isOngoing || sbn.packageName == c.packageName || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
            val e = n.extras
            // Riproduzioni (musica, podcast, video): non sono messaggi da leggere.
            if (e.containsKey(Notification.EXTRA_MEDIA_SESSION) || n.category == Notification.CATEGORY_TRANSPORT) return null
            // «Notifiche silenziose» del pannello: importanza bassa o minima.
            val r = Ranking()
            if (ranking?.getRanking(sbn.key, r) == true && r.importance <= android.app.NotificationManager.IMPORTANCE_LOW) return null
            val title = (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: e.getCharSequence(Notification.EXTRA_TITLE))
                ?.toString().orEmpty()
            // Chat: gli ultimi messaggi con il mittente; liste (es. posta): le righe; altrimenti il testo.
            @Suppress("DEPRECATION")
            val messages = e.getParcelableArray(Notification.EXTRA_MESSAGES).orEmpty().mapNotNull { it as? android.os.Bundle }
                .mapNotNull { b ->
                    val text = b.getCharSequence("text")?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    b.getCharSequence("sender")?.let { "$it: $text" } ?: text
                }
            val lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty().map { it.toString() }
            val text = when {
                messages.isNotEmpty() -> messages.takeLast(6).joinToString(" / ")
                lines.isNotEmpty() -> lines.take(6).joinToString(" / ")
                else -> (e.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            }
            if (title.isBlank() && text.isBlank()) return null
            val pm = c.packageManager
            val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
            return Note(sbn.postTime, app, title.take(120), text.take(600), sbn.packageName, sbn.key)
        }
    }
}

object NoteStore {
    private const val DAY = 24 * 3_600_000L
    private fun file(c: Context) = File(c.filesDir, "notes.json")

    @Synchronized
    fun add(c: Context, note: Note) = write(c, (read(c) + note).takeLast(300))

    @Synchronized
    fun remove(c: Context, key: String) {
        val list = read(c)
        if (list.any { it.key == key }) write(c, list.filter { it.key != key })
    }

    /** Tiene solo le note delle notifiche [active]; quelle salvate senza chiave (versioni vecchie) escono. */
    @Synchronized
    fun keep(c: Context, active: Set<String>) {
        val list = read(c)
        val kept = list.filter { it.key in active }
        if (kept.size != list.size) write(c, kept)
    }

    private fun write(c: Context, list: List<Note>) = file(c).writeText(JSONArray(list.map {
        JSONObject().put("t", it.time).put("a", it.app).put("h", it.title).put("b", it.text).put("p", it.pkg).put("k", it.key)
    }).toString())

    @Synchronized
    fun read(c: Context): List<Note> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        val since = System.currentTimeMillis() - DAY
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { Note(it.getLong("t"), it.getString("a"), it.getString("h"), it.getString("b"), it.optString("p"), it.optString("k")) }
                .filter { it.time >= since }
        }.getOrDefault(emptyList())
    }

    fun hasAccess(c: Context): Boolean {
        val flat = Settings.Secure.getString(c.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(c, NotificationCollector::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/** Riassunto delle notifiche con Cloudflare Workers AI, usando la chiave personale dell'utente. */
object Summarizer {
    private const val MODEL = "@cf/meta/llama-3.3-70b-instruct-fp8-fast"

    private const val SYSTEM = "Sei l'assistente di un launcher dedicato al benessere digitale. Riassumi in italiano " +
        "le notifiche ancora da leggere, già divise per app e per conversazione, con tono calmo e sintetico. " +
        "Una riga per ogni conversazione (chat, persona, gruppo, thread di posta) o per ogni app se non ci sono " +
        "conversazioni; unisci le notifiche della stessa conversazione in un'unica frase che dice chi scrive e cosa " +
        "vuole. Metti prima ciò che richiede una risposta o un'azione, riduci a pochissime parole pubblicità e " +
        "promozioni. Al massimo 8 righe, testo semplice senza markdown, ogni riga inizia con il nome esatto dell'app " +
        "tra parentesi quadre, ad esempio: [WhatsApp] Calcetto: Marco e Luca chiedono se ci sei giovedì alle 20. " +
        "Scrivi solo delle app e delle notifiche presenti nell'elenco, mai di altre app. " +
        "Non scrivere mai righe per dire che non ci sono notifiche, che non ci sono altre notifiche importanti " +
        "o che non c'è nulla da leggere: se un'app non ha niente da dire, non scrivere nessuna riga per lei."

    private const val AGENDA = "Sei l'assistente di un launcher. Riscrivi in italiano ogni impegno in forma breve e " +
        "chiara, una riga per impegno, nello stesso ordine e sempre con lo stesso livello di dettaglio. Ogni riga inizia " +
        "con il numero dell'impegno e due punti, poi l'orario di inizio, cosa è (poche parole, senza ripetere il giorno) " +
        "e il luogo tra parentesi se c'è. Gli impegni di tutto il giorno non hanno orario. Non cambiare i numeri e non " +
        "saltare impegni. Puoi unire in una riga sola impegni simili dello STESSO giorno, con i numeri separati da virgola: " +
        "più compleanni diventano «compleanni di Marco e Anna», più turni uguali si scrivono una volta con tutti gli orari. " +
        "Niente commenti, consigli o frasi di contorno, niente markdown.\n" +
        "Esempio:\n1,2: compleanni di Marco e Anna\n3: 10:30 dentista (via Roma 3, Milano)\n4: 18:00 palestra (FitLife)"

    suspend fun summarize(account: String, token: String, notes: List<Note>): String {
        // Già raggruppate: un blocco per app, una riga per conversazione, dalla più recente.
        val digest = notes.sortedByDescending { it.time }.groupBy { it.app }.entries.joinToString("\n\n") { (app, list) ->
            "[$app]\n" + list.groupBy { it.title }.entries.joinToString("\n") { (title, msgs) ->
                "- ${title.ifBlank { "(senza titolo)" }}: " + msgs.reversed().joinToString(" / ") { it.text }.replace('\n', ' ').take(900)
            }
        }
        val names = notes.map { it.app }.distinct().joinToString(", ")
        return run(
            account, token, SYSTEM,
            "App con notifiche nel pannello (scrivi solo di queste): $names\n\nNotifiche ancora da leggere:\n$digest",
            temperature = 0.0,
        )
    }

    /** [digest]: un evento per riga, numerato, già con giorno e orario leggibili. */
    suspend fun summarizeAgenda(account: String, token: String, today: String, digest: String) =
        run(account, token, AGENDA, "Oggi è $today. Impegni:\n$digest", maxTokens = 1200, temperature = 0.0)

    private suspend fun run(
        account: String, token: String, system: String, user: String, maxTokens: Int = 600, temperature: Double? = null,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("max_tokens", maxTokens)
            // Temperatura 0: a parità di impegni il riassunto esce ogni volta uguale, con lo stesso dettaglio.
            .apply { temperature?.let { put("temperature", it) } }
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        val url = URL("https://api.cloudflare.com/client/v4/accounts/${account.trim()}/ai/run/$MODEL")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000; readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${token.trim()}")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(when (code) {
                400 -> "Richiesta rifiutata da Cloudflare. Controlla l'Account ID."
                401, 403 -> "Chiave API non valida o senza il permesso Workers AI."
                404 -> "Account ID non trovato."
                429 -> "Limite di richieste raggiunto. Riprova più tardi."
                else -> "Errore Cloudflare ($code)."
            })
            JSONObject(text).getJSONObject("result").getString("response").trim()
        } finally {
            conn.disconnect()
        }
    }
}
