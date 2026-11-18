package app.kanso.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

/** Stile grafico: Kanso (piatto), skeuomorfismo, Modern UI (Windows Phone), Nothing, Dueffe. */
enum class Style { KANSO, SKEUO, METRO, NOTHING, DUEFFE }
enum class Theme { SYSTEM, LIGHT, DARK }

enum class LockMode(val title: String, val hint: String) {
    NONE("Nessuno", "Impostazioni e Home sempre modificabili"),
    PIN("PIN", "Un codice di 4 cifre"),
    HOLD("Gesto", "Tieni premuto il cerchio per 3 secondi"),
}

/** Preferenze di Kanso, salvate in locale ed esposte come stato Compose. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("kanso", Context.MODE_PRIVATE)

    /** null finché l'utente non ha mai salvato una Home: allora si usano le app essenziali di sistema. */
    var tiles by mutableStateOf(sp.getString("tiles", null)?.split(',')?.filter { it.isNotBlank() })
        private set
    var friction by mutableStateOf(sp.getBoolean("friction", true)); private set
    var frictionSeconds by mutableStateOf(sp.getInt("frictionSeconds", 5)); private set
    var monochrome by mutableStateOf(sp.getBoolean("monochrome", true)); private set
    var showClock by mutableStateOf(sp.getBoolean("showClock", false)); private set
    var showTorch by mutableStateOf(sp.getBoolean("showTorch", true)); private set
    var showRinger by mutableStateOf(sp.getBoolean("showRinger", true)); private set
    var landscape by mutableStateOf(sp.getBoolean("landscape", false)); private set
    /** Id dei widget di Android nella pagina Consapevolezza, in ordine. */
    var widgets by mutableStateOf(sp.getString("widgets", "")!!.split(',').mapNotNull { it.toIntOrNull() }); private set
    var doubleTapLock by mutableStateOf(sp.getBoolean("doubleTapLock", false)); private set
    // Prima c'era solo l'interruttore "skeuo": chi lo aveva acceso resta sullo skeuomorfismo.
    var style by mutableStateOf(
        sp.getString("style", null)?.let { runCatching { Style.valueOf(it) }.getOrNull() }
            ?: if (sp.getBoolean("skeuo", false)) Style.SKEUO else Style.KANSO,
    ); private set
    // Di default scuro, com'era prima che si potesse scegliere.
    var theme by mutableStateOf(sp.getString("theme", null)?.let { runCatching { Theme.valueOf(it) }.getOrNull() } ?: Theme.DARK); private set
    var lockMode by mutableStateOf(LockMode.valueOf(sp.getString("lockMode", LockMode.NONE.name)!!)); private set
    // Chi aggiorna da una versione precedente ha già delle preferenze: niente presentazione.
    var onboarded by mutableStateOf(sp.getBoolean("onboarded", sp.all.isNotEmpty())); private set
    var cfAccount by mutableStateOf(sp.getString("cfAccount", "")!!); private set
    var cfToken by mutableStateOf(sp.getString("cfToken", "")!!); private set
    /** Categorie scelte a mano dall'utente, per package. Vincono su quelle indovinate. */
    var categories by mutableStateOf(
        sp.getString("categories", "")!!.split(',').mapNotNull { e ->
            val (pkg, cat) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            runCatching { pkg to Category.valueOf(cat) }.getOrNull()
        }.toMap(),
    ); private set

    /** App della scheda Lavoro in Home. */
    var workTiles by mutableStateOf(sp.getString("workTiles", "")!!.split(',').filter { it.isNotBlank() }); private set
    var workMode by mutableStateOf(runCatching { WorkMode.valueOf(sp.getString("workMode", "")!!) }.getOrDefault(WorkMode.NONE)); private set
    /** Giorni dell'orario fisso: bit 0 = lunedì … bit 6 = domenica. Di default dal lunedì al venerdì. */
    var workDays by mutableStateOf(sp.getInt("workDays", 0b0011111)); private set
    var workFixed by mutableStateOf(Shift(sp.getInt("workStart", 9 * 60), sp.getInt("workEnd", 18 * 60))); private set
    /** Turni per giorno (epochDay). Si tengono solo da una settimana fa in avanti. */
    var shifts by mutableStateOf(
        sp.getString("shifts", "")!!.split(',').mapNotNull { e ->
            val (day, start, end) = e.split('=', '-').takeIf { it.size == 3 }?.map { it.toLongOrNull() ?: return@mapNotNull null } ?: return@mapNotNull null
            day to Shift(start.toInt(), end.toInt())
        }.toMap(),
    ); private set
    /** Permesso o ferie: tra questi due istanti la scheda Lavoro non compare. */
    var pauseFrom by mutableStateOf(sp.getLong("pauseFrom", 0)); private set
    var pauseTo by mutableStateOf(sp.getLong("pauseTo", 0)); private set

    fun saveTiles(list: List<String>) { tiles = list; sp.edit().putString("tiles", list.joinToString(",")).apply() }
    fun saveWorkTiles(list: List<String>) { workTiles = list; sp.edit().putString("workTiles", list.joinToString(",")).apply() }
    fun saveWorkMode(v: WorkMode) { workMode = v; sp.edit().putString("workMode", v.name).apply() }
    fun saveWorkDays(v: Int) { workDays = v; sp.edit().putInt("workDays", v).apply() }
    fun saveWorkFixed(v: Shift) { workFixed = v; sp.edit().putInt("workStart", v.start).putInt("workEnd", v.end).apply() }
    fun saveShift(day: Long, shift: Shift?) {
        val oldest = java.time.LocalDate.now().toEpochDay() - 7
        shifts = (if (shift == null) shifts - day else shifts + (day to shift)).filterKeys { it >= oldest }
        sp.edit().putString("shifts", shifts.entries.joinToString(",") { "${it.key}=${it.value.start}-${it.value.end}" }).apply()
    }
    fun savePause(from: Long, to: Long) { pauseFrom = from; pauseTo = to; sp.edit().putLong("pauseFrom", from).putLong("pauseTo", to).apply() }
    fun saveCategory(pkg: String, category: Category) {
        categories = categories + (pkg to category)
        sp.edit().putString("categories", categories.entries.joinToString(",") { "${it.key}=${it.value.name}" }).apply()
    }
    fun saveFriction(v: Boolean) { friction = v; sp.edit().putBoolean("friction", v).apply() }
    fun saveFrictionSeconds(v: Int) { frictionSeconds = v; sp.edit().putInt("frictionSeconds", v).apply() }
    fun saveMonochrome(v: Boolean) { monochrome = v; sp.edit().putBoolean("monochrome", v).apply() }
    fun saveStyle(v: Style) { style = v; sp.edit().putString("style", v.name).apply() }
    fun saveTheme(v: Theme) { theme = v; sp.edit().putString("theme", v.name).apply() }
    fun saveShowClock(v: Boolean) { showClock = v; sp.edit().putBoolean("showClock", v).apply() }
    fun saveShowTorch(v: Boolean) { showTorch = v; sp.edit().putBoolean("showTorch", v).apply() }
    fun saveShowRinger(v: Boolean) { showRinger = v; sp.edit().putBoolean("showRinger", v).apply() }
    fun saveLandscape(v: Boolean) { landscape = v; sp.edit().putBoolean("landscape", v).apply() }
    fun saveWidgets(list: List<Int>) { widgets = list; sp.edit().putString("widgets", list.joinToString(",")).apply() }
    fun saveDoubleTapLock(v: Boolean) { doubleTapLock = v; sp.edit().putBoolean("doubleTapLock", v).apply() }
    fun saveOnboarded(v: Boolean = true) { onboarded = v; sp.edit().putBoolean("onboarded", v).apply() }
    fun saveLockMode(v: LockMode) { lockMode = v; sp.edit().putString("lockMode", v.name).apply() }
    fun saveCloudflare(account: String, token: String) {
        cfAccount = account.trim(); cfToken = token.trim()
        sp.edit().putString("cfAccount", cfAccount).putString("cfToken", cfToken).apply()
    }

    /** Ultimo riassunto AI dell'agenda, con quando è stato fatto e per quale giorno. */
    val agendaSummary get() = sp.getString("agendaSummary", null)
    val agendaSummaryAt get() = sp.getLong("agendaSummaryAt", 0)
    val agendaSummaryDay get() = sp.getInt("agendaSummaryDay", 0)
    fun saveAgendaSummary(text: String, at: Long, day: Int) {
        sp.edit().putString("agendaSummary", text).putLong("agendaSummaryAt", at).putInt("agendaSummaryDay", day).apply()
    }

    val hasPin get() = sp.contains("pinHash")

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        sp.edit().putString("pinSalt", salt).putString("pinHash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pinSalt", null) ?: return false
        return MessageDigest.isEqual(hash(salt, pin).toByteArray(), sp.getString("pinHash", "")!!.toByteArray())
    }

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
