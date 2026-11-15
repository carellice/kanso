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
}
