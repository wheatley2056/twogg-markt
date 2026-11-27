package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/** Reine Rechnungen des Markts (ohne Minecraft), getestet in {@code MarketMathTest}. */
public final class MarketMath {
    private MarketMath() {}

    public static final long MINUTE = 60_000L;

    /** € in Zehntel-Coins beim Kurs, aufgerundet auf 0,1 (wie die Preise der Economy). 0 € bleibt 0. */
    public static long priceTenths(double euro, double rate) {
        if (euro <= 0) return 0;
        if (rate <= 0) rate = 10.0;
        return Math.max(1, (long) Math.ceil(euro / rate * 10.0 - 1e-9));
    }

    /**
     * Theke (seit 0.5.2): Ist das Angebot seit der Anzeige teurer oder kleiner geworden? {@code shownPrice}/{@code shownCount}
     * schickt der Client mit; 0 = unbekannt (ältere Clients), dann wird nicht verglichen.
     */
    public static boolean offerWorse(long shownPrice, double shownCount, long price, int count) {
        return (shownPrice > 0 && price > shownPrice) || (shownCount > 0 && count < shownCount);
    }

    /** Coins (1,5) in Zehntel (15), kaufmännisch gerundet. */
    public static long tenths(double coins) {
        return Math.round(coins * 10.0);
    }

    /**
     * Neues Mietende, wenn {@code periods} Zeiträume dazugebucht werden: ab jetzt oder ab dem bisherigen Ende,
     * je nachdem, was später ist.
     */
    public static long extend(long rentedUntil, long now, int periods, long periodMs) {
        return Math.max(rentedUntil, now) + periods * periodMs;
    }

    /** Wie viele Zeiträume lassen sich noch dazubuchen, ohne über {@code maxPeriods} im Voraus zu kommen? */
    public static int bookable(long rentedUntil, long now, int maxPeriods, long periodMs) {
        long ahead = Math.max(0, rentedUntil - now);
        long used = (ahead + periodMs - 1) / periodMs;   // angefangene zählen
        return (int) Math.max(0, maxPeriods - used);
    }

    /** Ganze, noch nicht angefangene Zeiträume bis zum Mietende. */
    public static int unusedPeriods(long rentedUntil, long now, long periodMs) {
        if (rentedUntil <= now) return 0;
        return (int) ((rentedUntil - now) / periodMs);
    }

    /** Anteil (Prozent) eines Betrags, abgerundet auf 0,1 Coin. */
    public static long percent(long tenths, double percent) {
        if (tenths <= 0 || percent <= 0) return 0;
        return (long) Math.floor(tenths * percent / 100.0 + 1e-9);
    }

    /** Marktgebühr: aufgerundet, mindestens 0,1 Coin wenn der Satz größer 0 ist, höchstens der Betrag. */
    public static long fee(long tenths, double percent) {
        if (tenths <= 0 || percent <= 0) return 0;
        return Math.min(tenths, Math.max(1, (long) Math.ceil(tenths * percent / 100.0 - 1e-9)));
    }

    /**
     * Ankaufspreis pro Bündel bei bisher {@code sold} verkauften Bündeln: voll bis 0 verkauft, dann linear fallend,
     * bei {@code demand} Bündeln auf {@code minFactor}. Kaufmännisch gerundet auf 0,1 Coin.
     */
    public static long buyerPrice(double basePrice, double sold, int demand, double minFactor) {
        double f = 1.0 - (1.0 - minFactor) * Math.min(1.0, Math.max(0, sold) / Math.max(1, demand));
        return Math.round(tenths(basePrice) * f);
    }

    /** Verkaufte Menge klingt ab: in {@code recoverMinutes} um genau {@code demand} Bündel. */
    public static double decay(double sold, int demand, long elapsedMs, int recoverMinutes) {
        if (sold <= 0 || elapsedMs <= 0) return Math.max(0, sold);
        double per = demand / (double) (recoverMinutes * MINUTE);
        return Math.max(0, sold - per * elapsedMs);
    }

    /** {@code n} verschiedene Vorlagen nach Gewicht ziehen. */
    public static List<MarktConfig.JobTemplate> pick(Collection<MarktConfig.JobTemplate> pool, int n, Random rnd) {
        List<MarktConfig.JobTemplate> left = new ArrayList<>(pool);
        List<MarktConfig.JobTemplate> out = new ArrayList<>();
        while (out.size() < n && !left.isEmpty()) {
            int total = 0;
            for (MarktConfig.JobTemplate t : left) total += Math.max(1, t.weight);
            int r = rnd.nextInt(total);
            for (int i = 0; i < left.size(); i++) {
                r -= Math.max(1, left.get(i).weight);
                if (r < 0) {
                    out.add(left.remove(i));
                    break;
                }
            }
        }
        return out;
    }

    /** "12,5" / "3" – deutsches Komma, wie die Economy. */
    public static String coins(long tenths) {
        long abs = Math.abs(tenths);
        String s = (abs / 10) + (abs % 10 == 0 ? "" : "," + (abs % 10));
        return tenths < 0 ? "-" + s : s;
    }

    /** Dauer in Worten: „2 T 4 Std“, „35 Min“. */
    public static String duration(long ms) {
        if (ms <= 0) return "abgelaufen";
        long min = ms / MINUTE;
        long d = min / 1440, h = (min % 1440) / 60, m = min % 60;
        if (d > 0) return d + " T " + h + " Std";
        if (h > 0) return h + " Std " + m + " Min";
        return Math.max(1, m) + " Min";
    }

    /** ID aus einem Namen: Kleinbuchstaben, a–z 0–9 _ -. */
    public static String slug(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
        t = t.replaceAll("[^a-z0-9_-]+", "_").replaceAll("_+", "_");
        if (t.startsWith("_")) t = t.substring(1);
        if (t.endsWith("_")) t = t.substring(0, t.length() - 1);
        return t.length() > 32 ? t.substring(0, 32) : t;
    }

    /**
     * Losziehung: {@code places} verschiedene Gewinner, Chance proportional zur Zahl der Lose. Weniger Teilnehmer als
     * Plätze → entsprechend weniger Gewinner.
     */
    public static List<String> drawLottery(java.util.Map<String, Integer> tickets, int places, Random rnd) {
        java.util.Map<String, Integer> left = new java.util.TreeMap<>(tickets);
        left.values().removeIf(v -> v == null || v <= 0);
        List<String> out = new ArrayList<>();
        while (out.size() < places && !left.isEmpty()) {
            int total = 0;
            for (int v : left.values()) total += v;
            int r = rnd.nextInt(total);
            for (var e : left.entrySet()) {
                r -= e.getValue();
                if (r < 0) {
                    out.add(e.getKey());
                    left.remove(e.getKey());
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Wichteln: Index des Empfängers für jedes Geschenk (Geber an Stelle i). Niemand bekommt sein eigenes, solange es
     * mindestens zwei Teilnehmer gibt (zufällige Reihenfolge, dann eins weiterreichen).
     */
    public static int[] wichtel(int n, Random rnd) {
        int[] to = new int[n];
        if (n <= 1) {
            if (n == 1) to[0] = 0;
            return to;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        java.util.Collections.shuffle(order, rnd);
        for (int k = 0; k < n; k++) to[order.get(k)] = order.get((k + 1) % n);
        return to;
    }

    /** Die besten {@code n} Einträge (größter Wert zuerst, bei Gleichstand nach Schlüssel), nur Werte > 0. */
    public static List<java.util.Map.Entry<String, Long>> top(java.util.Map<String, Long> values, int n) {
        List<java.util.Map.Entry<String, Long>> l = new ArrayList<>();
        for (var e : values.entrySet()) if (e.getValue() != null && e.getValue() > 0) l.add(java.util.Map.entry(e.getKey(), e.getValue()));
        l.sort((a, b) -> b.getValue().equals(a.getValue()) ? a.getKey().compareTo(b.getKey()) : Long.compare(b.getValue(), a.getValue()));
        return l.size() > n ? l.subList(0, n) : l;
    }
}
