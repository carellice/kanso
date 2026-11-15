package app.kanso.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class Contact(val id: Long, val lookup: String, val name: String, val number: String)

private val streetWords = listOf(
    "via", "viale", "v.le", "piazza", "p.za", "p.zza", "piazzale", "corso", "c.so", "largo", "vicolo", "strada",
    "lungomare", "lungotevere", "contrada", "località", "loc.", "borgo", "salita", "galleria", "rotonda",
    "street", "st.", "avenue", "ave", "road", "rd.", "boulevard", "blvd", "lane", "rue", "calle", "platz", "straße", "strasse",
)

/**
 * true se il testo sembra un indirizzo: "via Roma 12", "Piazza Duomo, Milano", "10 Downing Street",
 * oppure un CAP italiano seguito da una città.
 */
fun looksLikeAddress(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.length < 6) return false
    val words = q.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
    if (words.size < 2) return false
    // Il tipo di strada all'inizio, oppure dopo un numero civico ("10 downing street").
    if (words.first() in streetWords) return true
    if (words.first().any(Char::isDigit) && words.drop(1).any { it in streetWords }) return true
    if (words.size >= 3 && words.last() in streetWords) return true
    return Regex("\\b\\d{5}\\s+[a-zà-ù]{3,}").containsMatchIn(q)
}

/** true per "example.com" o "https://...": apre il sito invece di cercarlo. */
fun looksLikeUrl(query: String): Boolean {
    val q = query.trim()
    if (q.contains(' ')) return false
    return q.startsWith("http://") || q.startsWith("https://") ||
        Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}(/\\S*)?$", RegexOption.IGNORE_CASE).matches(q)
}

fun normalize(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** App che corrispondono alla ricerca: prima quelle che iniziano con il testo, poi le altre parole. */
fun matchApps(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = normalize(query.trim())
    if (q.isEmpty()) return emptyList()
    return apps.mapNotNull { app ->
        val label = normalize(app.label)
        when {
            label.startsWith(q) -> 0 to app
            label.split(' ').any { it.startsWith(q) } -> 1 to app
            q.length >= 3 && label.contains(q) -> 2 to app
            else -> null
        }
    }.sortedBy { it.first }.map { it.second }.take(6)
}

fun mapsUri(query: String): Uri = Uri.parse("geo:0,0?q=" + Uri.encode(query.trim()))

fun webUri(query: String): Uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim()))

fun siteUri(query: String): Uri = Uri.parse(query.trim().let { if (it.startsWith("http")) it else "https://$it" })

suspend fun searchContacts(context: Context, query: String): List<Contact> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query.trim())),
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            buildList {
                while (c.moveToNext() && size < 12) add(Contact(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty()))
            }.distinctBy { it.id }.take(4)
        }.orEmpty()
    }.getOrDefault(emptyList())
}
