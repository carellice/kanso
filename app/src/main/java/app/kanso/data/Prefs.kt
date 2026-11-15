package app.kanso.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.security.SecureRandom

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
    var columns by mutableStateOf(sp.getInt("columns", 3)); private set
    var friction by mutableStateOf(sp.getBoolean("friction", true)); private set
    var frictionSeconds by mutableStateOf(sp.getInt("frictionSeconds", 5)); private set
    var monochrome by mutableStateOf(sp.getBoolean("monochrome", true)); private set
    var showClock by mutableStateOf(sp.getBoolean("showClock", false)); private set
    var lockMode by mutableStateOf(LockMode.valueOf(sp.getString("lockMode", LockMode.NONE.name)!!)); private set
    var cfAccount by mutableStateOf(sp.getString("cfAccount", "")!!); private set
    var cfToken by mutableStateOf(sp.getString("cfToken", "")!!); private set

    fun saveTiles(list: List<String>) { tiles = list; sp.edit().putString("tiles", list.joinToString(",")).apply() }
    fun saveColumns(v: Int) { columns = v; sp.edit().putInt("columns", v).apply() }
    fun saveFriction(v: Boolean) { friction = v; sp.edit().putBoolean("friction", v).apply() }
    fun saveFrictionSeconds(v: Int) { frictionSeconds = v; sp.edit().putInt("frictionSeconds", v).apply() }
    fun saveMonochrome(v: Boolean) { monochrome = v; sp.edit().putBoolean("monochrome", v).apply() }
    fun saveShowClock(v: Boolean) { showClock = v; sp.edit().putBoolean("showClock", v).apply() }
    fun saveLockMode(v: LockMode) { lockMode = v; sp.edit().putString("lockMode", v.name).apply() }
    fun saveCloudflare(account: String, token: String) {
        cfAccount = account.trim(); cfToken = token.trim()
        sp.edit().putString("cfAccount", cfAccount).putString("cfToken", cfToken).apply()
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
