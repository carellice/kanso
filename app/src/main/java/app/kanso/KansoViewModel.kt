package app.kanso

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kanso.data.AppEntry
import app.kanso.data.AppsRepository
import app.kanso.data.DayUsage
import app.kanso.data.DistractingCategories
import app.kanso.data.LockMode
import app.kanso.data.NoteStore
import app.kanso.data.Prefs
import app.kanso.data.Summarizer
import app.kanso.data.UsageRepository
import kotlinx.coroutines.launch

sealed interface SummaryState {
    data object Idle : SummaryState
    data object Loading : SummaryState
    data class Done(val text: String, val count: Int) : SummaryState
    data class Failed(val message: String) : SummaryState
}

class KansoViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val prefs = Prefs(app)
    val appsRepo = AppsRepository(app)
    val usageRepo = UsageRepository(app)

    var apps by mutableStateOf<List<AppEntry>>(emptyList()); private set
    var usage by mutableStateOf<DayUsage?>(null); private set
    var hasUsageAccess by mutableStateOf(false); private set
    var hasNotificationAccess by mutableStateOf(false); private set
    var noteCount by mutableIntStateOf(0); private set
    var agenda by mutableStateOf<List<app.kanso.data.AgendaEvent>>(emptyList()); private set
    var hasCalendarAccess by mutableStateOf(false); private set
    var summary by mutableStateOf<SummaryState>(SummaryState.Idle); private set
    private var defaultTiles by mutableStateOf<List<String>>(emptyList())

    // Sovrapposizioni aperte sopra le tre pagine.
    var searchOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var pickerOpen by mutableStateOf(false)
    var friction by mutableStateOf<AppEntry?>(null); private set
    var unlock by mutableStateOf<(() -> Unit)?>(null); private set
    /** Aumenta a ogni pressione del tasto Home, per tornare alla pagina centrale. */
    var homeSignal by mutableIntStateOf(0); private set

    val tiles: List<String> get() = (prefs.tiles ?: defaultTiles).filter { pkg -> apps.isEmpty() || apps.any { it.pkg == pkg } }
    val byPkg: Map<String, AppEntry> get() = apps.associateBy { it.pkg }

    fun refresh() = viewModelScope.launch {
        apps = appsRepo.load()
        if (defaultTiles.isEmpty()) defaultTiles = appsRepo.essentials()
        hasUsageAccess = usageRepo.hasAccess()
        hasNotificationAccess = NoteStore.hasAccess(ctx)
        noteCount = NoteStore.read(ctx).size
        usage = usageRepo.today()
        refreshAgenda()
    }

    fun refreshAgenda() = viewModelScope.launch {
        hasCalendarAccess = ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        agenda = if (hasCalendarAccess) app.kanso.data.loadAgenda(ctx) else emptyList()
    }

    fun onHomePressed() {
        searchOpen = false; settingsOpen = false; pickerOpen = false; editing = false
        friction = null; unlock = null
        homeSignal++
    }

    /** Le app della Home si aprono subito; le altre passano dall'attrito consapevole. */
    fun open(app: AppEntry) {
        if (prefs.friction && app.pkg !in tiles) friction = app else launch(app.pkg)
    }

    fun isDistracting(app: AppEntry) = app.category in DistractingCategories

    fun confirmFriction() { friction?.let { launch(it.pkg) }; friction = null }
    fun cancelFriction() { friction = null }

    fun launch(pkg: String) {
        val intent = appsRepo.launchIntent(pkg) ?: return toast("App non disponibile")
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
    }

    fun view(uri: Uri) = start(Intent(Intent.ACTION_VIEW, uri))

    fun start(intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            searchOpen = false
        } catch (_: ActivityNotFoundException) {
            toast("Nessuna app può aprirlo")
        }
    }

    /** Esegue [action] solo dopo PIN o gesto, se il blocco è attivo. */
    fun guarded(action: () -> Unit) {
        val locked = when (prefs.lockMode) {
            LockMode.NONE -> false
            LockMode.PIN -> prefs.hasPin
            LockMode.HOLD -> true
        }
        if (locked) unlock = action else action()
    }

    fun unlocked() { val a = unlock; unlock = null; a?.invoke() }
    fun cancelUnlock() { unlock = null }

    fun addTile(pkg: String) { if (pkg !in tiles) prefs.saveTiles(tiles + pkg) }
    fun removeTile(pkg: String) = prefs.saveTiles(tiles - pkg)
    fun moveTile(pkg: String, by: Int) {
        val list = tiles.toMutableList()
        val i = list.indexOf(pkg)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.add(j, list.removeAt(i))
        prefs.saveTiles(list)
    }

    fun summarize() {
        val notes = NoteStore.read(ctx)
        when {
            prefs.cfAccount.isBlank() || prefs.cfToken.isBlank() ->
                summary = SummaryState.Failed("Inserisci Account ID e chiave API di Cloudflare nelle impostazioni.")
            notes.isEmpty() -> summary = SummaryState.Done("Nessuna notifica nelle ultime 24 ore. Respira: è tutto tranquillo.", 0)
            else -> {
                summary = SummaryState.Loading
                viewModelScope.launch {
                    summary = runCatching { Summarizer.summarize(prefs.cfAccount, prefs.cfToken, notes) }
                        .fold({ SummaryState.Done(it, notes.size) }, { SummaryState.Failed(it.message ?: "Connessione non riuscita.") })
                }
            }
        }
    }

    fun dismissSummary() { summary = SummaryState.Idle }

    private fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
}
