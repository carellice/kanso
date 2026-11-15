package app.kanso.data

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone

data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String,
    /** Giorno locale dell'evento come yyyyMMdd, per raggrupparli. */
    val day: Int,
)

/** Impegni dei calendari visibili, da inizio giornata ai prossimi [days] giorni. */
suspend fun loadAgenda(context: Context, days: Int = 7): List<AgendaEvent> = withContext(Dispatchers.IO) {
    val start = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val from = start.timeInMillis
    val to = start.apply { add(Calendar.DAY_OF_YEAR, days) }.timeInMillis
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        .also { ContentUris.appendId(it, from); ContentUris.appendId(it, to) }.build()
    val now = System.currentTimeMillis()
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR,
                CalendarContract.Instances.EVENT_LOCATION,
            ),
            "${CalendarContract.Instances.VISIBLE} = 1", null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    val begin = c.getLong(2); val end = c.getLong(3)
                    // Già finito. Per gli eventi di tutto il giorno conta l'ultimo giorno, letto in UTC.
                    if (if (allDay) dayKey(end - 1, true) < dayKey(now, false) else end < now) continue
                    add(AgendaEvent(
                        c.getLong(0), c.getString(1).orEmpty().ifBlank { "(senza titolo)" }, begin, end, allDay,
                        c.getInt(5), c.getString(6).orEmpty(), dayKey(begin, allDay),
                    ))
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
        // Gli eventi di più giorni ancora in corso partono da oggi.
        .map { if (it.day < dayKey(now, false)) it.copy(day = dayKey(now, false)) else it }
        .sortedWith(compareBy({ it.day }, { !it.allDay }, { it.begin }))
        .take(30)
}

/** Gli eventi "tutto il giorno" sono salvati in UTC: la data va letta in UTC, non nel fuso locale. */
fun dayKey(time: Long, utc: Boolean): Int {
    val c = if (utc) Calendar.getInstance(TimeZone.getTimeZone("UTC")) else Calendar.getInstance()
    c.timeInMillis = time
    return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)
}

fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
