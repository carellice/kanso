package app.kanso.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun addresses() {
        listOf("via Roma 12", "Piazza Duomo, Milano", "10 Downing Street", "corso Buenos Aires 5 Milano", "20121 Milano centro")
            .forEach { assertTrue(it, looksLikeAddress(it)) }
        listOf("instagram", "meteo domani", "via", "pizza margherita").forEach { assertFalse(it, looksLikeAddress(it)) }
    }

    @Test fun urls() {
        assertTrue(looksLikeUrl("github.com"))
        assertTrue(looksLikeUrl("https://example.org/x"))
        assertFalse(looksLikeUrl("ciao mondo"))
        assertFalse(looksLikeUrl("telegram"))
    }

    @Test fun appMatching() {
        val apps = listOf("WhatsApp", "Foto", "Google Maps", "Mappe").map { AppEntry(it, it, Category.ALTRO) }
        assertEquals(listOf("Mappe", "Google Maps"), matchApps(apps, "map").map { it.label })
        assertEquals(listOf("Foto"), matchApps(apps, "fo").map { it.label })
        assertTrue(matchApps(apps, " ").isEmpty())
    }

    @Test fun usageSplitsAcrossHours() {
        val h = 3_600_000L
        val u = summarize(listOf(Session("a", h / 2, h + h / 4), Session("b", 2 * h, 2 * h + 60_000), Session("a", 5, 5)), 0, 3)
        assertEquals(h * 3 / 4 + 60_000, u.screenMs)
        assertEquals(h / 2, u.hourly[0]); assertEquals(h / 4, u.hourly[1]); assertEquals(60_000L, u.hourly[2])
        assertEquals("a", u.top.first().first)
        assertEquals(3, u.unlocks)
    }

    @Test fun workShifts() {
        val mon = java.time.LocalDate.of(2026, 11, 16)
        val fixed = { d: java.time.LocalDate -> shiftOn(d, WorkMode.FIXED, 0b0011111, Shift(540, 1080), emptyMap()) }
        assertEquals(mon.atTime(18, 0), shiftEnd(mon.atTime(9, 0), fixed))
        assertEquals(null, shiftEnd(mon.atTime(18, 0), fixed))
        assertEquals(null, shiftEnd(mon.plusDays(5).atTime(10, 0), fixed)) // sabato
        // Turno di notte: 22-6 di lunedì vale ancora martedì alle 3.
        val night = mapOf(mon.toEpochDay() to Shift(22 * 60, 6 * 60))
        val shifts = { d: java.time.LocalDate -> shiftOn(d, WorkMode.SHIFTS, 0, Shift(0, 0), night) }
        assertEquals(mon.plusDays(1).atTime(6, 0), shiftEnd(mon.plusDays(1).atTime(3, 0), shifts))
        assertEquals(null, shiftEnd(mon.atTime(21, 59), shifts))
        assertEquals(null, shiftEnd(mon.atTime(10, 0)) { shiftOn(it, WorkMode.NONE, 0b1111111, Shift(0, 1440), emptyMap()) })
    }
}
