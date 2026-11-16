package app.kanso.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

/** Stile grafico: Kanso (piatto), skeuomorfismo, Modern UI (Windows Phone), Nothing, Dueffe. */
enum class Style { KANSO, SKEUO, METRO, NOTHING, DUEFFE }

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
    // Prima c'era solo l'interruttore "skeuo": chi lo aveva acceso resta sullo skeuomorfismo.
    var style by mutableStateOf(
        sp.getString("style", null)?.let { runCatching { Style.valueOf(it) }.getOrNull() }
            ?: if (sp.getBoolean("skeuo", false)) Style.SKEUO else Style.KANSO,
    ); private set
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

    fun saveTiles(list: List<String>) { tiles = list; sp.edit().putString("tiles", list.joinToString(",")).apply() }
    fun saveCategory(pkg: String, category: Category) {
        categories = categories + (pkg to category)
        sp.edit().putString("categories", categories.entries.joinToString(",") { "${it.key}=${it.value.name}" }).apply()
    }
    fun saveFriction(v: Boolean) { friction = v; sp.edit().putBoolean("friction", v).apply() }
    fun saveFrictionSeconds(v: Int) { frictionSeconds = v; sp.edit().putInt("frictionSeconds", v).apply() }
    fun saveMonochrome(v: Boolean) { monochrome = v; sp.edit().putBoolean("monochrome", v).apply() }
    fun saveStyle(v: Style) { style = v; sp.edit().putString("style", v.name).apply() }
    fun saveShowClock(v: Boolean) { showClock = v; sp.edit().putBoolean("showClock", v).apply() }
    fun saveShowTorch(v: Boolean) { showTorch = v; sp.edit().putBoolean("showTorch", v).apply() }
    fun saveShowRinger(v: Boolean) { showRinger = v; sp.edit().putBoolean("showRinger", v).apply() }
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
