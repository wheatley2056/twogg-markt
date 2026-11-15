package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.List;

/** Auftrag am Auftragsbrett: Marktauftrag (vom Server, aus dem Pool) oder Spielerauftrag. */
public final class Job {
    public String id = "";
    public boolean system = true;
    public String title = "";
    /** Item-ID; geliefert werden Stapel mit dieser ID (unbeschädigt). */
    public String item = "";
    public int count = 1;
    /** Belohnung pro Erledigung in Zehntel-Coins. */
    public long reward = 10;
    /** Wie oft der Auftrag erledigt werden kann (Spielerauftrag: 1). */
    public int slots = 1;
    public List<String> doneBy = new ArrayList<>();
    public long created;
    public long expires;
    /** Nur Spielerauftrag: wer ihn ausgehängt hat. */
    public String poster = "";
    public String posterName = "";

    public int remaining() {
        return Math.max(0, slots - doneBy.size());
    }

    public boolean open(long now) {
        return remaining() > 0 && (expires <= 0 || now < expires);
    }
}
