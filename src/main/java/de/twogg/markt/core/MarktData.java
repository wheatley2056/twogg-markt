package de.twogg.markt.core;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Weltdaten des Markts ({@code <welt>/twogg-markt/markt.json}). */
public final class MarktData {
    public int version = 1;
    public Map<String, Plot> plots = new LinkedHashMap<>();
    /** Server-Läden (seit 0.4.0): ID → Laden. */
    public Map<String, Laden> laeden = new LinkedHashMap<>();
    /** Räumdienst-Zonen: ID → Bereich. */
    public Map<String, Zone> zones = new LinkedHashMap<>();
    public List<Job> jobs = new ArrayList<>();
    public long lastJobRefresh;
    public int nextJob = 1;

    /** Ankauf: verkaufte Bündel pro Item, klingt mit der Zeit ab (siehe {@link MarketMath#decay}). */
    public Map<String, Double> sold = new HashMap<>();
    public long soldUpdated;

    /** Tageszähler pro Spieler (Ankauf je Item, Schnee). Werden bei Tageswechsel geleert. */
    public String day = "";
    public Map<String, Daily> daily = new HashMap<>();

    /** Offene Auszahlungen pro Spieler (Kasse bei Ablauf, Erstattungen): werden beim nächsten Login gezahlt. */
    public Map<String, Pending> pending = new HashMap<>();
    /** Abholfach: gelieferte Ware für Spieleraufträge, als serialisierte Stapel. */
    public Map<String, List<JsonElement>> pickup = new HashMap<>();

    /** Weihnachtsmarkt-Event. */
    public MarktEvent event = new MarktEvent();
    /** Marktgelände (Musik, Atmosphäre); null = ganzer Server. */
    public Box area;
    /** Bestenliste-Tafel: Ort und Hologramm. */
    public Box leaderboard;
    public String leaderboardHolo = "";
    /** Adventskalender: Jahr und geöffnete Türchen pro Spieler. */
    public int adventYear;
    public Map<String, List<Integer>> advent = new HashMap<>();
    /** Spieler (UUID), die das Markt-Handbuch schon bekommen haben (seit 0.3.0). */
    public java.util.Set<String> guideGiven = new java.util.HashSet<>();

    public static final class Zone {
        public String id = "";
        public String name = "";
        public Box box = new Box();
    }

    public static final class Daily {
        public Map<String, Integer> bought = new HashMap<>();
        public int snowBlocks;
        public long snowPaid;
    }

    public static final class Pending {
        public long amount;
        public String name = "";
        public List<String> reasons = new ArrayList<>();
    }

    public void sanitize() {
        if (plots == null) plots = new LinkedHashMap<>();
        if (laeden == null) laeden = new LinkedHashMap<>();
        for (Laden l : laeden.values()) {
            if (l.sells == null) l.sells = new ArrayList<>();
            if (l.buys == null) l.buys = new ArrayList<>();
            if (l.waren == null) l.waren = new ArrayList<>();
        }
        if (zones == null) zones = new LinkedHashMap<>();
        if (jobs == null) jobs = new ArrayList<>();
        if (sold == null) sold = new HashMap<>();
        if (daily == null) daily = new HashMap<>();
        if (pending == null) pending = new HashMap<>();
        if (pickup == null) pickup = new HashMap<>();
        if (day == null) day = "";
        if (event == null) event = new MarktEvent();
        event.sanitize();
        if (advent == null) advent = new HashMap<>();
        if (guideGiven == null) guideGiven = new java.util.HashSet<>();
        if (leaderboardHolo == null) leaderboardHolo = "";
        plots.values().removeIf(p -> p == null || p.box == null);
        for (Map.Entry<String, Plot> e : plots.entrySet()) {
            Plot p = e.getValue();
            p.id = e.getKey();
            if (p.name == null || p.name.isBlank()) p.name = p.id;
            if (p.note == null) p.note = "";
            if (p.mode == null) p.mode = Plot.Mode.BOTH;
            if (p.owner == null) p.owner = "";
            if (p.ownerName == null) p.ownerName = "";
            if (p.helpers == null) p.helpers = new LinkedHashMap<>();
            if (p.offers == null) p.offers = new ArrayList<>();
            p.offers.removeIf(o -> o == null || o.item == null);
            if (p.hologram == null) p.hologram = "";
            p.maxPeriods = Math.max(1, p.maxPeriods);
            p.rentPrice = Math.max(0, p.rentPrice);
            p.buyPrice = Math.max(0, p.buyPrice);
        }
        zones.values().removeIf(z -> z == null || z.box == null);
        jobs.removeIf(j -> j == null || j.item == null || j.item.isBlank());
        for (Job j : jobs) {
            if (j.doneBy == null) j.doneBy = new ArrayList<>();
            if (j.poster == null) j.poster = "";
            if (j.posterName == null) j.posterName = "";
            if (j.title == null) j.title = "";
        }
    }

    public Plot plotAt(String dim, int x, int y, int z) {
        for (Plot p : plots.values()) if (p.box.contains(dim, x, y, z)) return p;
        return null;
    }

    public Zone zoneAt(String dim, int x, int y, int z) {
        for (Zone zn : zones.values()) if (zn.box.contains(dim, x, y, z)) return zn;
        return null;
    }

    public Daily daily(String uuid) {
        return daily.computeIfAbsent(uuid, k -> new Daily());
    }

    public void addPending(String uuid, String name, long amount, String reason) {
        if (amount <= 0) return;
        Pending p = pending.computeIfAbsent(uuid, k -> new Pending());
        p.amount += amount;
        if (name != null && !name.isBlank()) p.name = name;
        if (p.reasons.size() < 20) p.reasons.add(reason);
    }

    public Job job(String id) {
        for (Job j : jobs) if (j.id.equals(id)) return j;
        return null;
    }
}
