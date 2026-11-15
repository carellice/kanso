package app.kanso.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Note(val time: Long, val app: String, val title: String, val text: String)

/**
 * Tiene una copia locale delle notifiche delle ultime 24 ore, solo per il riassunto.
 * Non le cancella, non le nasconde e non risponde: la gestione di Android resta intatta.
 */
class NotificationCollector : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.isOngoing || sbn.packageName == packageName || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: n.extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }
            .getOrDefault(sbn.packageName)
        NoteStore.add(this, Note(sbn.postTime, app, title.take(120), text.take(400)))
    }
}

object NoteStore {
    private const val DAY = 24 * 3_600_000L
    private fun file(c: Context) = File(c.filesDir, "notes.json")

    @Synchronized
    fun add(c: Context, note: Note) {
        val list = (read(c) + note).takeLast(300)
        file(c).writeText(JSONArray(list.map {
            JSONObject().put("t", it.time).put("a", it.app).put("h", it.title).put("b", it.text)
        }).toString())
    }

    @Synchronized
    fun read(c: Context): List<Note> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        val since = System.currentTimeMillis() - DAY
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { arr.getJSONObject(it) }
                .map { Note(it.getLong("t"), it.getString("a"), it.getString("h"), it.getString("b")) }
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
        "le notifiche ricevute, con tono calmo e sintetico: raggruppa per persona o app, evidenzia solo ciò che " +
        "richiede davvero attenzione e ignora pubblicità e promozioni. Al massimo 6 punti brevi, testo semplice " +
        "senza markdown, ogni punto su una riga che inizia con il carattere •."

    suspend fun summarize(account: String, token: String, notes: List<Note>): String = withContext(Dispatchers.IO) {
        val digest = notes.joinToString("\n") { "[${it.app}] ${it.title}: ${it.text}".replace('\n', ' ') }
        val body = JSONObject()
            .put("max_tokens", 600)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", "Notifiche delle ultime 24 ore:\n$digest")))
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
