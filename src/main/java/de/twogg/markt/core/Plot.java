package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Marktstand (Parzelle), vom Admin eingezeichnet. Kann gemietet und/oder gekauft werden.
 * Zeiten in Millisekunden seit Epoch, Beträge in Zehntel-Coins, Preise in der Referenzwährung (€) des Economy-Profils.
 */
public final class Plot {
    public enum Mode { RENT, BUY, BOTH, LOCKED }

    public enum Status { FREE, RENTED, OWNED, LOCKED }

    public String id = "";
    public String name = "";
    /** Freitext vom Admin, z. B. „Glühweinbude am Eingang“. */
    public String note = "";
    public Box box = new Box();
    public Mode mode = Mode.BOTH;
    /** Miete pro Zeitraum in €, wird beim aktuellen Kurs in Coins umgerechnet. */
    public double rentPrice = 15.0;
    /** Kaufpreis in €. */
    public double buyPrice = 400.0;
    /** So viele Zeiträume darf man höchstens im Voraus mieten. */
    public int maxPeriods = 7;

    public String owner = "";
    public String ownerName = "";
    public boolean bought;
    public long rentedUntil;
    /** Zuletzt pro Zeitraum gezahlt (für die Erstattung beim Kündigen). */
    public long periodPaid;
    /** Beim Kauf gezahlt (für den Rückkauf). */
    public long boughtFor;
    public long since;
    /** Warnstufe vor Ablauf: 0 keine, 1 eine Stunde, 2 zehn Minuten. */
    public int warned;

    public Map<String, String> helpers = new LinkedHashMap<>();

    /** Verkaufstheke: geöffnet, Angebote, Kasse. */
    public boolean shopOpen = true;
    public List<Offer> offers = new ArrayList<>();
    public long till;
    public long salesTotal;
    public int nextOffer = 1;
    /** UUID des Hologramms über dem Stand (TextDisplay), leer = noch keins. */
    public String hologram = "";

    public boolean hasOwner() {
        return owner != null && !owner.isEmpty();
    }

    public Status status(long now) {
        if (hasOwner() && (bought || rentedUntil > now)) return bought ? Status.OWNED : Status.RENTED;
        return mode == Mode.LOCKED ? Status.LOCKED : Status.FREE;
    }

    public boolean active(long now) {
        Status s = status(now);
        return s == Status.RENTED || s == Status.OWNED;
    }

    public boolean rentable() {
        return mode == Mode.RENT || mode == Mode.BOTH;
    }

    public boolean buyable() {
        return mode == Mode.BUY || mode == Mode.BOTH;
    }

    /** Besitzer oder Helfer (nur solange der Stand vergeben ist). */
    public boolean member(String uuid, long now) {
        if (!active(now) || uuid == null) return false;
        return uuid.equals(owner) || helpers.containsKey(uuid);
    }

    public boolean isOwner(String uuid, long now) {
        return active(now) && uuid != null && uuid.equals(owner);
    }

    /** Vergabe aufheben (Ablauf, Kündigung, Räumung). Kasse und Angebote fasst der Aufrufer vorher an. */
    public void release() {
        owner = "";
        ownerName = "";
        bought = false;
        rentedUntil = 0;
        periodPaid = 0;
        boughtFor = 0;
        since = 0;
        warned = 0;
        helpers.clear();
        offers.clear();
        shopOpen = true;
    }

    public Offer offer(String offerId) {
        for (Offer o : offers) if (o.id.equals(offerId)) return o;
        return null;
    }
}
