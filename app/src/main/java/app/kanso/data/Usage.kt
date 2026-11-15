package app.kanso.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class DayUsage(
    val screenMs: Long,
    val unlocks: Int,
    /** Millisecondi di schermo per ciascuna delle 24 ore di oggi. */
    val hourly: List<Long>,
    /** Tutte le app usate oggi, in ordine decrescente: package e millisecondi. */
    val top: List<Pair<String, Long>>,
    val perApp: Map<String, Long>,
)

class UsageRepository(private val context: Context) {

    fun hasAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Tempo schermo e sblocchi da mezzanotte a ora, ricostruiti dagli eventi di sistema. */
    suspend fun today(): DayUsage? = withContext(Dispatchers.IO) {
        if (!hasAccess()) return@withContext null
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, now)
        val e = UsageEvents.Event()
        val sessions = mutableListOf<Session>()
        val open = HashMap<String, Long>()
        var unlocks = 0
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open.putIfAbsent(e.packageName, e.timeStamp)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(e.packageName)?.let { sessions += Session(e.packageName, it, e.timeStamp) }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                // Schermo spento: chiude tutto quello che risultava aperto.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    open.forEach { (p, t) -> sessions += Session(p, t, e.timeStamp) }; open.clear()
                }
            }
        }
        open.forEach { (p, t) -> sessions += Session(p, t, now) }
        summarize(sessions.filter { it.pkg != context.packageName }, start, unlocks)
    }
}

data class Session(val pkg: String, val from: Long, val to: Long)

/** Somma le sessioni per app e per ora. Separata per poterla testare senza Android. */
fun summarize(sessions: List<Session>, dayStart: Long, unlocks: Int): DayUsage {
    val hour = 3_600_000L
    val hourly = LongArray(24)
    val perApp = HashMap<String, Long>()
    for (s in sessions) {
        if (s.to <= s.from) continue
        perApp.merge(s.pkg, s.to - s.from, Long::plus)
        var t = s.from
        while (t < s.to) {
            val h = ((t - dayStart) / hour).toInt().coerceIn(0, 23)
            val end = minOf(s.to, dayStart + (h + 1) * hour)
            // Oltre le 24 ore (cambio d'ora): il resto va nell'ultima ora.
            if (end <= t) { hourly[h] += s.to - t; break }
            hourly[h] += end - t
            t = end
        }
    }
    return DayUsage(
        screenMs = perApp.values.sum(),
        unlocks = unlocks,
        hourly = hourly.toList(),
        top = perApp.entries.sortedByDescending { it.value }.map { it.key to it.value },
        perApp = perApp,
    )
}

fun formatDuration(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "meno di 1 min"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${m % 60} min"
    }
}
