package app.kanso.data

import java.time.LocalDate
import java.time.LocalDateTime

/** Come si lavora: niente, sempre agli stessi orari oppure a turni scelti settimana per settimana. */
enum class WorkMode(val title: String) { NONE("Nessuno"), FIXED("Orario fisso"), SHIFTS("Turni") }

/** Un turno in minuti dalla mezzanotte. Se [end] non supera [start] il turno finisce il giorno dopo (notte). */
data class Shift(val start: Int, val end: Int) {
    override fun toString() = "${hhmm(start)}–${hhmm(end)}"
}

fun hhmm(minutes: Int) = "%02d:%02d".format(minutes / 60 % 24, minutes % 60)

/** Il turno previsto per [day], o null se quel giorno non si lavora. [days]: bit 0 = lunedì … bit 6 = domenica. */
fun shiftOn(day: LocalDate, mode: WorkMode, days: Int, fixed: Shift, shifts: Map<Long, Shift>): Shift? = when (mode) {
    WorkMode.NONE -> null
    WorkMode.FIXED -> fixed.takeIf { days and (1 shl (day.dayOfWeek.value - 1)) != 0 }
    WorkMode.SHIFTS -> shifts[day.toEpochDay()]
}

/** Quando finisce il turno in corso a [now], o null se ora non si lavora. Conta anche il turno di notte di ieri. */
fun shiftEnd(now: LocalDateTime, shiftOn: (LocalDate) -> Shift?): LocalDateTime? {
    val today = now.toLocalDate()
    for (day in listOf(today, today.minusDays(1))) {
        val s = shiftOn(day) ?: continue
        val from = day.atStartOfDay().plusMinutes(s.start.toLong())
        val to = day.atStartOfDay().plusMinutes(s.end.toLong() + if (s.end <= s.start) 1440 else 0)
        if (!now.isBefore(from) && now.isBefore(to)) return to
    }
    return null
}
