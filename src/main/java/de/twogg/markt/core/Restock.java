package de.twogg.markt.core;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Wöchentlicher Nachschub eines Ladens mit Lager (seit 0.6.0 ohne Minecraft, getestet in {@code RestockTest}).
 * Termine gelten in der Zeitzone des Ladens (Standard Europe/Berlin), auch über Sommer- und Winterzeit: Fällt die
 * Uhrzeit in die Lücke der Zeitumstellung (z. B. 02:30 am letzten Sonntag im März), gilt die erste gültige Uhrzeit
 * danach (03:30); in der doppelten Stunde im Oktober gilt das erste Vorkommen.
 */
public final class Restock {
    private Restock() {}

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Berlin");

    public static ZoneId zone(String zone) {
        try {
            return ZoneId.of(zone == null ? "" : zone.trim());
        } catch (RuntimeException e) {
            return DEFAULT_ZONE;
        }
    }

    /** Wochentag aus {@code MONDAY} … {@code SUNDAY} (auch klein geschrieben); sonst {@code fallback}. */
    public static DayOfWeek day(String day, DayOfWeek fallback) {
        try {
            return DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Uhrzeit „hh:mm“; sonst {@code fallback}. */
    public static LocalTime time(String time, LocalTime fallback) {
        try {
            return LocalTime.parse(time.trim().length() == 4 ? "0" + time.trim() : time.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Letzter planmäßiger Termin ≤ {@code now} (ms seit Epoch). */
    public static long lastSlot(long now, DayOfWeek day, LocalTime time, ZoneId zone) {
        ZonedDateTime n = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime slot = at(n.toLocalDate().with(TemporalAdjusters.previousOrSame(day)), time, zone);
        if (slot.isAfter(n)) slot = at(slot.toLocalDate().minusWeeks(1), time, zone);
        return slot.toInstant().toEpochMilli();
    }

    /** Nächster Termin &gt; {@code now} (ms seit Epoch). */
    public static long nextSlot(long now, DayOfWeek day, LocalTime time, ZoneId zone) {
        ZonedDateTime last = Instant.ofEpochMilli(lastSlot(now, day, time, zone)).atZone(zone);
        return at(last.toLocalDate().plusWeeks(1), time, zone).toInstant().toEpochMilli();
    }

    /** Ist seit der letzten Lieferung {@code lastDone} ein Termin vergangen? (Server aus über mehrere Wochen = einmal.) */
    public static boolean due(long lastDone, long now, DayOfWeek day, LocalTime time, ZoneId zone) {
        return lastDone < lastSlot(now, day, time, zone);
    }

    private static ZonedDateTime at(java.time.LocalDate date, LocalTime time, ZoneId zone) {
        // ofLocal: Lücke → um die Länge der Lücke nach vorn, Überlappung → früherer Zeitpunkt
        return ZonedDateTime.ofLocal(date.atTime(time), zone, null);
    }

    /**
     * Bestand nach dem Nachschub: Fehlendes wird bis zum Soll aufgefüllt (nicht obendrauf gelegt). Was Spieler über das
     * Soll hinaus verkauft haben, bleibt liegen ({@code trimSurplus = false}) oder geht mit dem Schiff zurück
     * ({@code true}, Verhalten bis 0.5.2). Nie über den Lagerplatz.
     */
    public static long restock(long stock, long target, long capacity, boolean trimSurplus) {
        long t = Math.max(0, Math.min(capacity, target));
        long s = Math.max(0, Math.min(capacity, stock));
        return trimSurplus ? t : Math.max(s, t);
    }
}
