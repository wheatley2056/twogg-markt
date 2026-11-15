package de.twogg.markt.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Öffnungszeiten des Weihnachtsmarkts. Einträge wie {@code "Fr 18:00-21:00"}, {@code "Sa,So 16:00-20:00"},
 * {@code "täglich 18:00-20:00"} oder {@code "24.12. 16:00-19:00"}. Endet ein Fenster vor dem Beginn, geht es über
 * Mitternacht. Fehlerhafte Einträge werden ignoriert ({@link #errors()}).
 */
public final class Schedule {
    /** Ein konkretes Öffnungsfenster. {@code key} ist eindeutig (Eintrag + Datum), damit es nur einmal startet. */
    public record Window(String key, LocalDateTime start, LocalDateTime end) {}

    private record Entry(String text, boolean[] days, int day, int month, LocalTime from, LocalTime to) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private static final String[] DAYS = {"mo", "di", "mi", "do", "fr", "sa", "so"};

    public Schedule(List<String> lines) {
        if (lines == null) return;
        for (String l : lines) {
            if (l == null || l.isBlank()) continue;
            try {
                entries.add(parse(l.trim()));
            } catch (RuntimeException e) {
                errors.add(l);
            }
        }
    }

    public List<String> errors() {
        return errors;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static Entry parse(String s) {
        int sp = s.lastIndexOf(' ');
        if (sp < 0) throw new IllegalArgumentException(s);
        String when = s.substring(0, sp).trim().toLowerCase(Locale.ROOT), time = s.substring(sp + 1).trim();
        String[] t = time.split("-");
        if (t.length != 2) throw new IllegalArgumentException(s);
        LocalTime from = LocalTime.parse(pad(t[0])), to = LocalTime.parse(pad(t[1]));
        boolean[] days = new boolean[7];
        int day = 0, month = 0;
        if (when.matches("\\d{1,2}\\.\\d{1,2}\\.?")) {
            String[] d = when.replaceAll("\\.$", "").split("\\.");
            day = Integer.parseInt(d[0]);
            month = Integer.parseInt(d[1]);
            if (month < 1 || month > 12 || day < 1 || day > 31) throw new IllegalArgumentException(s);
        } else if (when.equals("täglich") || when.equals("taeglich") || when.equals("*")) {
            java.util.Arrays.fill(days, true);
        } else {
            for (String part : when.split(",")) {
                String p = part.trim();
                String[] range = p.split("-");
                int a = dayIndex(range[0]), b = range.length > 1 ? dayIndex(range[1]) : a;
                for (int i = a; ; i = (i + 1) % 7) {
                    days[i] = true;
                    if (i == b) break;
                }
            }
        }
        return new Entry(s, days, day, month, from, to);
    }

    private static String pad(String t) {
        String x = t.trim();
        return x.length() == 4 ? "0" + x : x;   // 9:00 → 09:00
    }

    private static int dayIndex(String d) {
        String x = d.trim();
        for (int i = 0; i < 7; i++) if (x.startsWith(DAYS[i])) return i;
        throw new IllegalArgumentException(d);
    }

    private boolean matchesDate(Entry e, LocalDate date) {
        if (e.month() > 0) return date.getMonthValue() == e.month() && date.getDayOfMonth() == e.day();
        return e.days()[date.getDayOfWeek().getValue() - 1];
    }

    private Window window(Entry e, LocalDate date) {
        LocalDateTime start = date.atTime(e.from());
        LocalDateTime end = e.to().isAfter(e.from()) ? date.atTime(e.to()) : date.plusDays(1).atTime(e.to());
        return new Window(e.text() + "@" + date, start, end);
    }

    /** Das Fenster, das gerade läuft (auch eins, das gestern begonnen hat und über Mitternacht geht), sonst null. */
    public Window active(LocalDateTime now) {
        for (Entry e : entries) {
            for (LocalDate d : new LocalDate[]{now.toLocalDate(), now.toLocalDate().minusDays(1)}) {
                if (!matchesDate(e, d)) continue;
                Window w = window(e, d);
                if (!now.isBefore(w.start()) && now.isBefore(w.end())) return w;
            }
        }
        return null;
    }

    /** Nächstes Fenster, das nach {@code now} beginnt (bis 400 Tage voraus), sonst null. */
    public Window next(LocalDateTime now) {
        Window best = null;
        for (Entry e : entries) {
            for (int i = 0; i < 400; i++) {
                LocalDate d = now.toLocalDate().plusDays(i);
                if (!matchesDate(e, d)) continue;
                Window w = window(e, d);
                if (w.start().isAfter(now)) {
                    if (best == null || w.start().isBefore(best.start())) best = w;
                    break;
                }
            }
        }
        return best;
    }

    /** Für die Anzeige: „Fr 18:00–21:00“. */
    public static String pretty(String line) {
        return line.replace("-", "–");
    }
}
