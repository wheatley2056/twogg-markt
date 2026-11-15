package de.twogg.markt.core;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Zustand des Weihnachtsmarkt-Events (Teil der Weltdaten). Zeiten in ms, Beträge in Zehntel-Coins. */
public final class MarktEvent {
    public enum Phase { CLOSED, OPENING, OPEN, FINALE }

    public Phase phase = Phase.CLOSED;
    public int round;
    public long startedAt;
    public long endsAt;
    /** Ende der Eröffnungs- bzw. Finalphase. */
    public long phaseUntil;
    /** Fenster der Öffnungszeiten, das zuletzt gestartet (oder manuell beendet) wurde – startet nicht noch einmal. */
    public String windowKey = "";

    public Map<String, Stats> stats = new HashMap<>();
    /** Wähler → Stand-ID. */
    public Map<String, String> votes = new HashMap<>();
    public Map<String, Integer> tickets = new HashMap<>();
    public long pot;
    public List<Gift> wichtel = new ArrayList<>();
    /** „uuid@x,y,z“ gefundener Geschenke in diesem Event. */
    public List<String> giftsFound = new ArrayList<>();

    /** Ergebnisse des letzten Events (für die Anzeige). */
    public List<String> lastResults = new ArrayList<>();
    public long lastEnded;

    public static final class Stats {
        public String name = "";
        public long sales;
        public long snow;
        public long jobs;
        public long gifts;
    }

    public static final class Gift {
        public String from = "";
        public String name = "";
        public JsonElement item;
    }

    public boolean running() {
        return phase == Phase.OPENING || phase == Phase.OPEN;
    }

    public Stats stats(String uuid, String name) {
        Stats s = stats.computeIfAbsent(uuid, k -> new Stats());
        if (name != null && !name.isBlank()) s.name = name;
        return s;
    }

    /** Neues Event: Zähler leeren. */
    public void reset() {
        stats.clear();
        votes.clear();
        tickets.clear();
        pot = 0;
        wichtel.clear();
        giftsFound.clear();
    }

    /** Stimmen pro Stand. */
    public Map<String, Long> voteCount() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String plot : votes.values()) m.merge(plot, 1L, Long::sum);
        return m;
    }

    public void sanitize() {
        if (phase == null) phase = Phase.CLOSED;
        if (windowKey == null) windowKey = "";
        if (stats == null) stats = new HashMap<>();
        if (votes == null) votes = new HashMap<>();
        if (tickets == null) tickets = new HashMap<>();
        if (wichtel == null) wichtel = new ArrayList<>();
        wichtel.removeIf(g -> g == null || g.item == null || g.from == null);
        if (giftsFound == null) giftsFound = new ArrayList<>();
        if (lastResults == null) lastResults = new ArrayList<>();
    }
}
