package app.kanso

import android.app.ActivityOptions
import android.app.Application
import android.os.Bundle
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserHandle
import app.kanso.data.PrivateApp
import app.kanso.data.PrivateSpace
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
import app.kanso.data.Category
import app.kanso.data.DayUsage
import app.kanso.data.DistractingCategories
import app.kanso.data.LockMode
import app.kanso.data.NoteStore
import app.kanso.data.NotificationCollector
import app.kanso.data.Chrono
import app.kanso.data.Prefs
import app.kanso.data.Summarizer
import app.kanso.data.UsageRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface SummaryState {
    data object Idle : SummaryState
    data object Loading : SummaryState
    /** [apps]: nome dell'app in minuscolo -> pacchetto, per mostrare le icone accanto alle righe. */
    data class Done(val text: String, val count: Int, val apps: Map<String, String> = emptyMap()) : SummaryState
    data class Failed(val message: String) : SummaryState
}

const val AGENDA_DAYS = 30
private const val AGENDA_SUMMARY_EVERY = 5 * 3_600_000L
private val Tag = Regex("""^[•\-*\s]*\[([^\]]+)]""")
private val EmptyLine = Regex(
    """(non ci sono|non c'è|nessuna?|nessun'|niente|nient'|nulla)\s*(altr[aeio]|nuov[aeio]|di)?\s*(notifich|messagg|novit|important|altro|da (leggere|segnalare))""",
    RegexOption.IGNORE_CASE,
)

class KansoViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    val appContext: android.content.Context get() = ctx
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
    /** Riassunto AI dell'agenda per la Home; null se l'AI non è configurata o non ha ancora risposto. */
    var agendaSummary by mutableStateOf<String?>(null); private set
    var agendaSummarizing by mutableStateOf(false); private set
    private var defaultTiles by mutableStateOf<List<String>>(emptyList())

    // Sovrapposizioni aperte sopra le tre pagine.
    var searchOpen by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var editing by mutableStateOf(false)
    var pickerOpen by mutableStateOf(false)
    var agendaOpen by mutableStateOf(false)
    var notesOpen by mutableStateOf(false)
    var friction by mutableStateOf<AppEntry?>(null); private set
    var unlock by mutableStateOf<(() -> Unit)?>(null); private set
    /** Aumenta a ogni pressione del tasto Home, per tornare alla pagina centrale. */
    var homeSignal by mutableIntStateOf(0); private set
    /** Aumenta quando si torna in Kanso da un'app aperta da qui, per l'animazione di ritorno. */
    var returnSignal by mutableIntStateOf(0); private set
    private var away = false
    /** Aumenta quando si apre un'app: la Home si allontana mentre l'app cresce. */
    var departSignal by mutableIntStateOf(0); private set
    /** La vista della Home, per far partire l'animazione di apertura dal centro. */
    var rootView: java.lang.ref.WeakReference<android.view.View>? = null

    private val privateSpace = PrivateSpace(app)
    /** Profilo dello spazio privato, null se non c'è o se Kanso non è l'app Home. */
    var privateUser by mutableStateOf<UserHandle?>(null); private set
    var privateLocked by mutableStateOf(true); private set
    var privateApps by mutableStateOf<List<PrivateApp>>(emptyList()); private set

    // Android avvisa quando lo spazio privato si sblocca o si blocca, anche da fuori Kanso.
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { refreshPrivate() }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE); addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED); addAction(Intent.ACTION_PROFILE_REMOVED)
            }
            ctx.registerReceiver(profileReceiver, filter, Context.RECEIVER_EXPORTED)
        }
    }

    override fun onCleared() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) runCatching { ctx.unregisterReceiver(profileReceiver) }
    }

    fun refreshPrivate() = viewModelScope.launch {
        val user = privateSpace.profile()
        privateUser = user
        privateLocked = user == null || privateSpace.isLocked(user)
        privateApps = if (user != null && !privateLocked) privateSpace.apps(user) else emptyList()
    }

    fun togglePrivate() {
        val user = privateUser ?: return
        if (!privateSpace.setLocked(user, !privateLocked)) toast("Spazio privato non disponibile")
    }

    fun launchPrivate(app: PrivateApp) { if (privateSpace.launch(app)) away = true else toast("App non disponibile") }

    val tiles: List<String> get() = (prefs.tiles ?: defaultTiles).filter { pkg -> apps.isEmpty() || apps.any { it.pkg == pkg } }
    val byPkg: Map<String, AppEntry> get() = apps.associateBy { it.pkg }

    fun refresh() = viewModelScope.launch {
        apps = appsRepo.load().map { a -> prefs.categories[a.pkg]?.let { a.copy(category = it) } ?: a }
        if (defaultTiles.isEmpty()) defaultTiles = appsRepo.essentials()
        refreshPrivate()
        hasUsageAccess = usageRepo.hasAccess()
        hasNotificationAccess = NoteStore.hasAccess(ctx)
        if (hasNotificationAccess) {
            // Se Android ha staccato il servizio delle notifiche (succede dopo gli aggiornamenti) lo si ricollega.
            val active = NotificationCollector.active()
            if (active == null) NotificationCollector.rebind(ctx)
            else { Chrono.sync(ctx, active); NoteStore.keep(ctx, active.map { it.key }.toSet()) }
        }
        noteCount = currentNotes().size
        usage = usageRepo.today()
        refreshAgenda()
    }

    fun refreshAgenda() = viewModelScope.launch {
        hasCalendarAccess = ctx.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        agenda = if (hasCalendarAccess) app.kanso.data.loadAgenda(ctx, AGENDA_DAYS) else emptyList()
        summarizeAgenda(force = false)
    }

    /** Il tasto in Home: rifà subito il riassunto dell'agenda. */
    fun regenerateAgenda() = viewModelScope.launch { summarizeAgenda(force = true) }

    /**
     * Con Cloudflare configurato la Home mostra il riassunto dei prossimi 7 giorni. Si rifà da solo al massimo
     * ogni 5 ore o quando cambia il giorno (Oggi e Domani non sarebbero più giusti); altrimenti con il tasto.
     */
    private suspend fun summarizeAgenda(force: Boolean) {
        if (prefs.cfAccount.isBlank() || prefs.cfToken.isBlank() || agenda.isEmpty()) {
            agendaSummary = null; return
        }
        val now = System.currentTimeMillis()
        val todayKey = app.kanso.data.dayKey(now, false)
        val cached = prefs.agendaSummary?.takeIf { prefs.agendaSummaryDay == todayKey }
        agendaSummary = cached
        if (!force && cached != null && now - prefs.agendaSummaryAt < AGENDA_SUMMARY_EVERY) return
        if (agendaSummarizing) return
        val until = app.kanso.data.dayKey(now + 7 * 86_400_000L, false)
        val day = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN)
        val time = SimpleDateFormat("HH:mm", Locale.ITALIAN)
        val digest = agenda.filter { it.day < until }.take(40).joinToString("\n") { e ->
            val date = Calendar.getInstance().apply { set(e.day / 10000, e.day / 100 % 100 - 1, e.day % 100) }.time
            // L'etichetta (Oggi, Domani, Giovedì) è quella con cui la Home abbina la frase alla pagina del giorno.
            val label = when (e.day) {
                app.kanso.data.dayKey(now, false) -> "Oggi"
                app.kanso.data.dayKey(now + 86_400_000L, false) -> "Domani"
                else -> SimpleDateFormat("EEEE", Locale.ITALIAN).format(date).replaceFirstChar { it.uppercase() }
            }
            val d = "$label (${SimpleDateFormat("d MMMM", Locale.ITALIAN).format(date)})"
            val t = if (e.allDay) "tutto il giorno" else "${time.format(Date(e.begin))}-${time.format(Date(e.end))}"
            "$d, $t: ${e.title}" + if (e.location.isNotBlank()) " (${e.location})" else ""
        }
        val today = day.format(Date(now))
        if (digest.isBlank()) return
        agendaSummarizing = true
        // In caso di errore resta il riassunto precedente (o gli impegni); si riprova al prossimo ritorno in Home.
        runCatching { Summarizer.summarizeAgenda(prefs.cfAccount, prefs.cfToken, today, digest) }
            .onSuccess { agendaSummary = it; prefs.saveAgendaSummary(it, now, todayKey) }
            .onFailure { if (force) toast("Riassunto non riuscito: ${it.message}") }
        agendaSummarizing = false
    }

    /** Apre la navigazione verso il luogo di un impegno: Google Maps se c'è, altrimenti l'app di mappe. */
    fun navigate(place: String) {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place)))
        if (nav.resolveActivity(ctx.packageManager) != null) start(nav) else view(Uri.parse("geo:0,0?q=" + Uri.encode(place)))
    }

    fun onHomePressed() {
        searchOpen = false; settingsOpen = false; pickerOpen = false; editing = false
        agendaOpen = false; notesOpen = false
        friction = null; unlock = null
        homeSignal++
    }

    /** Le app della Home si aprono subito; le altre passano dall'attrito consapevole. */
    fun open(app: AppEntry, options: Bundle? = null) {
        if (prefs.friction && app.pkg !in tiles) { friction = app; frictionOptions = options } else launch(app.pkg, options)
    }

    fun isDistracting(app: AppEntry) = app.category in DistractingCategories

    private var frictionOptions: Bundle? = null
    fun confirmFriction() { friction?.let { launch(it.pkg, frictionOptions) }; friction = null }
    fun cancelFriction() { friction = null }

    /** [options]: animazione che parte dall'icona; senza, l'app cresce dal centro. */
    fun launch(pkg: String, options: Bundle? = null) {
        val intent = appsRepo.launchIntent(pkg) ?: return toast("App non disponibile")
        start(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED), options)
    }

    fun resumed() { if (away) { away = false; returnSignal++ } }

    fun view(uri: Uri) = start(Intent(Intent.ACTION_VIEW, uri))

    fun start(intent: Intent, options: Bundle? = null) {
        try {
            // Android ignora le animazioni personalizzate tra app diverse (e mette lo scorrimento laterale):
            // quella che cresce da un rettangolo invece la rispetta. Senza icona di partenza, cresce dal centro.
            val anim = options ?: rootView?.get()?.let { v ->
                ActivityOptions.makeScaleUpAnimation(v, v.width / 2 - v.width / 8, v.height / 2 - v.height / 8, v.width / 4, v.height / 4).toBundle()
            }
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), anim)
            away = true
            departSignal++
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

    fun setCategory(pkg: String, category: Category) {
        prefs.saveCategory(pkg, category)
        apps = apps.map { if (it.pkg == pkg) it.copy(category = category) else it }
    }

    /** Riassunto pronto all'uso: accesso alle notifiche e chiavi Cloudflare presenti. */
    val summaryReady get() = hasNotificationAccess && prefs.cfAccount.isNotBlank() && prefs.cfToken.isNotBlank()

    /** Swipe verso il basso in Home: il riassunto se configurato, altrimenti la tendina di Android. */
    fun pullDown() {
        // Senza notifiche da riassumere non c'è niente da mostrare: si apre direttamente la tendina.
        if (summaryReady && currentNotes().isNotEmpty()) {
            notesOpen = true
            // Si rifà se nel frattempo sono arrivate o sono state tolte notifiche.
            if (summary !is SummaryState.Loading && (summary !is SummaryState.Done || summarizedNotes != currentNotes())) summarize()
        } else expandNotifications()
    }

    // API nascosta ma stabile, usata da molti launcher; serve EXPAND_STATUS_BAR.
    @android.annotation.SuppressLint("WrongConstant")
    fun expandNotifications() {
        runCatching {
            val sb = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        }.onFailure { toast("Apri le notifiche dall'alto dello schermo") }
    }

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

    private var summarizedNotes: List<app.kanso.data.Note>? = null

    /** Le notifiche ora nel pannello; se il servizio non è collegato, la copia locale. */
    private fun currentNotes(): List<app.kanso.data.Note> =
        NotificationCollector.activeNotes(ctx) ?: NoteStore.read(ctx)

    fun summarize() {
        val notes = currentNotes()
        summarizedNotes = notes
        when {
            prefs.cfAccount.isBlank() || prefs.cfToken.isBlank() ->
                summary = SummaryState.Failed("Inserisci Account ID e chiave API di Cloudflare nelle impostazioni.")
            // «Aggiorna» dopo aver tolto tutto dal pannello: il riassunto si chiude.
            notes.isEmpty() -> { summary = SummaryState.Idle; notesOpen = false }
            else -> {
                summary = SummaryState.Loading
                viewModelScope.launch {
                    val apps = notes.associate { n -> n.app.lowercase() to n.pkg }
                    summary = runCatching { Summarizer.summarize(prefs.cfAccount, prefs.cfToken, notes) }
                        .map { onlyRealApps(it, apps.keys) }
                        .fold({ SummaryState.Done(it, notes.size, apps.filterValues { p -> p.isNotEmpty() }) }, { SummaryState.Failed(it.message ?: "Connessione non riuscita.") })
                }
            }
        }
    }

    /**
     * Il modello a volte inventa app (Instagram senza Instagram) o scrive «nessun'altra notifica»: restano solo le righe
     * che iniziano con un'app presente davvero tra le notifiche del pannello e che dicono qualcosa.
     */
    private fun onlyRealApps(text: String, apps: Set<String>): String = text.lines().map { it.trim() }.filter { line ->
        val app = Tag.find(line)?.groupValues?.get(1)?.trim()?.lowercase() ?: return@filter false
        apps.any { it == app } && !EmptyLine.containsMatchIn(line)
    }.joinToString("\n")

    fun dismissSummary() { summary = SummaryState.Idle }

    fun toast(text: String) = Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
}
