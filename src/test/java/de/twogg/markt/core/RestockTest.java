package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Nachschub jeden Sonntag (Europe/Berlin), auch über Sommer- und Winterzeit (seit 0.6.0). */
class RestockTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime SIX = LocalTime.of(6, 0);

    private static long at(String utc) {
        return Instant.parse(utc).toEpochMilli();
    }

    @Test
    void lastAndNextSundayInWinter() {
        // Samstag, 28.11.2026, 12:00 MEZ
        long now = at("2026-11-28T11:00:00Z");
        assertEquals(at("2026-11-22T05:00:00Z"), Restock.lastSlot(now, DayOfWeek.SUNDAY, SIX, BERLIN), "So 22.11. 06:00 MEZ");
        assertEquals(at("2026-11-29T05:00:00Z"), Restock.nextSlot(now, DayOfWeek.SUNDAY, SIX, BERLIN), "So 29.11. 06:00 MEZ");
        // genau zum Termin: dieser gilt schon
        long slot = at("2026-11-29T05:00:00Z");
        assertEquals(slot, Restock.lastSlot(slot, DayOfWeek.SUNDAY, SIX, BERLIN));
        assertEquals(at("2026-11-22T05:00:00Z"), Restock.lastSlot(slot - 1, DayOfWeek.SUNDAY, SIX, BERLIN));
        // Sonntag vor 6 Uhr: noch der Termin der Vorwoche
        assertEquals(at("2026-11-22T05:00:00Z"), Restock.lastSlot(at("2026-11-29T04:30:00Z"), DayOfWeek.SUNDAY, SIX, BERLIN));
    }

    @Test
    void springForwardKeepsLocalSixOClock() {
        // Sommerzeit beginnt So 28.03.2027 um 02:00 → 03:00; 06:00 MESZ = 04:00 UTC
        long sat = at("2027-03-27T12:00:00Z");
        long next = Restock.nextSlot(sat, DayOfWeek.SUNDAY, SIX, BERLIN);
        assertEquals(at("2027-03-28T04:00:00Z"), next);
        long before = Restock.lastSlot(sat, DayOfWeek.SUNDAY, SIX, BERLIN);
        assertEquals(at("2027-03-21T05:00:00Z"), before, "Vorwoche noch MEZ");
        assertEquals(Duration.ofHours(7 * 24 - 1).toMillis(), next - before, "diese Woche hat eine Stunde weniger");
    }

    @Test
    void timeInsideTheGapMovesToTheFirstValidTime() {
        // 02:30 gibt es am 28.03.2027 nicht → 03:30 MESZ (01:30 UTC)
        long t = Restock.nextSlot(at("2027-03-27T12:00:00Z"), DayOfWeek.SUNDAY, LocalTime.of(2, 30), BERLIN);
        assertEquals(at("2027-03-28T01:30:00Z"), t);
        // und genau dann ist er fällig, nicht vorher
        assertFalse(Restock.due(at("2027-03-21T01:30:00Z"), t - 1, DayOfWeek.SUNDAY, LocalTime.of(2, 30), BERLIN));
        assertTrue(Restock.due(at("2027-03-21T01:30:00Z"), t, DayOfWeek.SUNDAY, LocalTime.of(2, 30), BERLIN));
    }

    @Test
    void fallBackKeepsLocalSixOClockAndUsesFirstOfTheDoubleHour() {
        // Winterzeit ab So 25.10.2026 03:00 → 02:00; 06:00 MEZ = 05:00 UTC
        long sat = at("2026-10-24T12:00:00Z");
        long next = Restock.nextSlot(sat, DayOfWeek.SUNDAY, SIX, BERLIN);
        assertEquals(at("2026-10-25T05:00:00Z"), next);
        assertEquals(Duration.ofHours(7 * 24 + 1).toMillis(), next - Restock.lastSlot(sat, DayOfWeek.SUNDAY, SIX, BERLIN));
        // 02:30 gibt es doppelt: das erste Mal (MESZ, 00:30 UTC)
        assertEquals(at("2026-10-25T00:30:00Z"), Restock.nextSlot(sat, DayOfWeek.SUNDAY, LocalTime.of(2, 30), BERLIN));
    }

    @Test
    void dueOncePerSlotEvenAfterLongDowntime() {
        long last = at("2026-11-08T05:00:00Z");                 // letzte Lieferung So 08.11.
        long now = at("2026-11-28T11:00:00Z");                  // drei Wochen später gestartet
        assertTrue(Restock.due(last, now, DayOfWeek.SUNDAY, SIX, BERLIN));
        long done = Restock.lastSlot(now, DayOfWeek.SUNDAY, SIX, BERLIN);
        assertFalse(Restock.due(done, now, DayOfWeek.SUNDAY, SIX, BERLIN), "nur einmal nachgeholt");
        assertFalse(Restock.due(done, at("2026-11-29T04:59:59Z"), DayOfWeek.SUNDAY, SIX, BERLIN));
        assertTrue(Restock.due(done, at("2026-11-29T05:00:00Z"), DayOfWeek.SUNDAY, SIX, BERLIN));
    }

    @Test
    void restockFillsUpToTargetButNotOnTop() {
        assertEquals(512, Restock.restock(0, 512, 1024, false), "leer → Soll");
        assertEquals(512, Restock.restock(300, 512, 1024, false), "aufgefüllt, nicht 300 + 512");
        assertEquals(800, Restock.restock(800, 512, 1024, false), "Verkauftes über dem Soll bleibt liegen");
        assertEquals(512, Restock.restock(800, 512, 1024, true), "mit trimSurplus geht der Überschuss zurück");
        assertEquals(1024, Restock.restock(5000, 512, 1024, false), "nie über den Lagerplatz");
        assertEquals(64, Restock.restock(0, 512, 64, false), "Soll über dem Lagerplatz → Lagerplatz");
    }

    @Test
    void parsingFallsBack() {
        assertEquals(DayOfWeek.SUNDAY, Restock.day("sunday", DayOfWeek.MONDAY));
        assertEquals(DayOfWeek.MONDAY, Restock.day("Sonntag", DayOfWeek.MONDAY));
        assertEquals(LocalTime.of(6, 0), Restock.time("6:00", LocalTime.NOON));
        assertEquals(LocalTime.NOON, Restock.time("25:99", LocalTime.NOON));
        assertEquals(BERLIN, Restock.zone("Mond/Krater"));
    }
}
