package de.twogg.markt.core;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-Laden (seit 0.4.0), kein Stand. Geöffnet wird er per Befehl ({@code /markt laden oeffnen <id> <spieler>}),
 * z. B. von einem Storyteller-NPC aus. Er verkauft zu festen Preisen ohne Bestand ({@link #sells}), kauft an
 * ({@link #buys}) und führt Waren mit Lagerbestand ({@link #waren}, seit 0.5.0). Brandts Lager (seit 0.6.0, siehe
 * {@link MarktConfig.Lager}) hat nur Waren mit Bestand.
 */
public final class Laden {
    public String id = "";
    public String name = "";
    /** Untertitel im Fenster (z. B. „Materiallager Station 7“). */
    public String subtitle = "";
    public boolean open = true;
    /** Verkauf an Spieler: Ware, Stück pro Kauf, Preis in Zehntel-Coins. */
    public List<Offer> sells = new ArrayList<>();
    /** Ankauf von Spielern. */
    public List<Buy> buys = new ArrayList<>();
    public int nextOffer = 1;

    /** Waren mit Lagerbestand und Preis nach Bestand (seit 0.5.0). */
    public List<Ware> waren = new ArrayList<>();
    /** Ankauf = Verkaufspreis × spread. */
    public double spread = 0.6;
    /** Wöchentliche Lieferung: Wochentag (MONDAY …), Uhrzeit, Zeitzone. Füllt bis zum Soll auf, Überschuss geht zurück. */
    public String deliveryDay = "MONDAY";
    public String deliveryTime = "06:00";
    public String zone = "Europe/Berlin";
    /** Zeitpunkt (ms) der letzten ausgeführten Lieferung. */
    public long lastDelivery;
    /**
     * Überschuss über dem Soll bei der Lieferung mitnehmen (Verhalten bis 0.5.2, Standard für vorhandene Läden). Brandts
     * Lager setzt es aus der Config ({@code lager.trimSurplus}, Standard aus: nur auffüllen).
     */
    public boolean trimSurplus = true;

    /** Eine Ware mit Lager: Preise in Zehnteln je Einheit ({@code unit} Stück), Bestände in Stück. */
    public static final class Ware {
        public String id = "";
        public JsonElement item;
        public String name = "";
        public int unit = 1;
        public long base = 10;
        public long capacity = 64;
        public long target = 32;
        public long stock;
        public long sold;
        public long bought;
        /** Seit 0.6.0: Ware kommt aus {@code lager.items} der Config (wird beim Abgleich geändert oder entfernt). */
        public boolean managed;
    }

    public Ware ware(String id) {
        for (Ware w : waren) if (w.id.equals(id)) return w;
        return null;
    }

    public static final class Buy {
        public String id = "";
        /** Vorlage; angekauft wird jedes unbeschädigte Item dieser Art (Daten egal). */
        public JsonElement item;
        public String name = "";
        /** Stück pro Ankauf. */
        public int count = 1;
        /** Zehntel-Coins pro Ankauf. */
        public long price = 10;
        /** Ankäufe pro Spieler und Tag, 0 = unbegrenzt. */
        public int dailyLimit;
        public long bought;
    }

    public Offer sell(String id) {
        for (Offer o : sells) if (o.id.equals(id)) return o;
        return null;
    }

    public Buy buy(String id) {
        for (Buy b : buys) if (b.id.equals(id)) return b;
        return null;
    }
}
