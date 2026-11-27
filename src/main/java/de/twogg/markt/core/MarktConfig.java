package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen des Markts ({@code config/twogg-markt/markt.json}). Beträge in Coins mit höchstens einer
 * Nachkommastelle, Preise für Stände in der Referenzwährung (€) des Economy-Profils.
 */
public final class MarktConfig {
    public String title = "Weihnachtsmarkt";
    public String subtitle = "Winterland";
    /** Akzentfarbe der Fenster. */
    public String accent = "#EE5A4F";

    public Stalls stalls = new Stalls();
    public Shop shop = new Shop();
    public Buyer buyer = new Buyer();
    public Jobs jobs = new Jobs();
    public Snow snow = new Snow();
    public Event event = new Event();
    /** Markt-Handbuch (seit 0.3.0): Buch zum Durchblättern. */
    public Guide guide = new Guide();

    public static final class Guide {
        /** Jeder Spieler bekommt das Handbuch beim ersten Betreten des Servers (einmal). */
        public boolean giveOnJoin = true;
    }

    public static final class Stalls {
        /** Länge eines Mietzeitraums in Echtzeit-Minuten (1440 = ein Tag). */
        public int periodMinutes = 1440;
        /** Anzeigename des Zeitraums („Tag“, „Woche“ …), Mehrzahl mit „e“/„n“ wird nicht gebildet. */
        public String periodName = "Tag";
        public String periodNamePlural = "Tage";
        /** So viele Stände darf ein Spieler gleichzeitig haben (gemietet + gekauft). */
        public int maxPerPlayer = 1;
        /** Helfer pro Stand (dürfen bauen, Kisten öffnen, die Theke bedienen). */
        public int maxHelpers = 4;
        /** Rückkauf eines gekauften Stands: so viel Prozent des Kaufpreises gibt es zurück. */
        public double buybackPercent = 50.0;
        /** Kündigung einer Miete: Erstattung ganzer, nicht angefangener Zeiträume in Prozent. */
        public double rentRefundPercent = 50.0;
        /** Beim Freiwerden den Stand auf den Zustand beim Einzeichnen zurücksetzen (Kisteninhalt geht verloren!). */
        public boolean resetOnRelease = false;
        /** Freie Stände sind geschützt: nur Admins bauen darin. */
        public boolean protectFree = true;
        /** Bezahlen mit Bargeld erlaubt (sonst nur vom Konto). */
        public boolean allowCash = true;
        /** Größter Stand beim Einzeichnen (Blöcke Grundfläche × Höhe). */
        public int maxVolume = 32 * 32 * 24;
        /** Hinweis in der Aktionsleiste beim Betreten eines Stands. */
        public boolean enterHint = true;
        /** Schwebende Schrift über jedem Stand (Name, Status, Preis bzw. Besitzer und Angebote). */
        public boolean holograms = true;
        /** Höhe des Hologramms über der Oberkante des Stands in Blöcken. */
        public double hologramOffset = 0.6;
    }

    public static final class Shop {
        /** Marktgebühr auf jeden Verkauf an der Theke in Prozent; sie wird vernichtet (Geld verlässt den Umlauf). */
        public double feePercent = 5.0;
        public int maxOffers = 12;
        /** Höchstpreis pro Kauf in Coins. */
        public double maxPrice = 500.0;
        /** Preis einer zusätzlichen Verkaufstheke in € (die erste gibt es beim Anmieten dazu). */
        public double counterPrice = 20.0;
    }

    public static final class Buyer {
        public boolean enabled = true;
        /** Bedarf erholt sich über so viele Minuten vollständig (Preis steigt wieder). */
        public int recoverMinutes = 1440;
        /** Tiefster Preis bei gesättigtem Bedarf, Anteil des Grundpreises. */
        public double minFactor = 0.3;
        /** Auszahlung als Bargeld statt aufs Konto. */
        public boolean payCash = false;
        public List<BuyerItem> items = new ArrayList<>();
    }

    public static final class BuyerItem {
        public String item = "";
        /** Anzeigename; leer = Name des Items. */
        public String name = "";
        /** Stück pro Ankauf. */
        public int bundle = 1;
        /** Coins pro Bündel bei vollem Bedarf. */
        public double price = 0.1;
        /** Bündel pro Tag, die der Markt zum vollen Preis braucht (danach fällt der Preis). */
        public int demand = 64;
        /** Bündel pro Spieler und Tag, 0 = unbegrenzt. */
        public int dailyLimit = 64;

        public BuyerItem() {}

        public BuyerItem(String item, String name, int bundle, double price, int demand, int dailyLimit) {
            this.item = item;
            this.name = name;
            this.bundle = bundle;
            this.price = price;
            this.demand = demand;
            this.dailyLimit = dailyLimit;
        }
    }

    public static final class Jobs {
        public boolean enabled = true;
        /** So viele Marktaufträge hängen gleichzeitig am Brett. */
        public int active = 4;
        /** So viele Spieler können denselben Marktauftrag erledigen (jeder einmal). */
        public int slots = 3;
        /** Neue Marktaufträge nach so vielen Minuten. */
        public int refreshMinutes = 1440;
        /** Spieler dürfen selbst Aufträge aushängen (Belohnung wird beim Aushängen vom Konto eingezogen). */
        public boolean playerJobs = true;
        public int maxPerPlayer = 3;
        /** Spieleraufträge laufen nach so vielen Stunden ab, die Belohnung geht zurück. */
        public int playerJobHours = 72;
        public double maxReward = 200.0;
        public List<JobTemplate> pool = new ArrayList<>();
    }

    public static final class JobTemplate {
        public String title = "";
        public String item = "";
        public int count = 1;
        public double reward = 1.0;
        public int weight = 1;

        public JobTemplate() {}

        public JobTemplate(String title, String item, int count, double reward) {
            this.title = title;
            this.item = item;
            this.count = count;
            this.reward = reward;
        }
    }

    public static final class Snow {
        public boolean enabled = true;
        /** Bezahlt wird je so viele geräumte Schneeblöcke (Schneeschicht oder Schneeblock). */
        public int blocksPerPay = 8;
        public double pay = 0.1;
        /** Höchstens so viel pro Spieler und Tag. */
        public double dailyMax = 3.0;
    }

    public static final class Event {
        public boolean enabled = true;
        /**
         * Öffnungszeiten, z. B. "Fr 18:00-21:00", "Sa,So 16:00-20:00", "täglich 18:00-20:00", "24.12. 16:00-19:00".
         * Leer (Standard): Der Markt öffnet nur per /markt event start.
         */
        public List<String> schedule = new ArrayList<>();
        /** Dauer bei /markt event start ohne Angabe. */
        public int defaultMinutes = 120;
        public int openingSeconds = 60;
        public int finaleSeconds = 90;
        /** Theke, Ankauf und Auftragsbrett nur während des Events offen. */
        public boolean onlyDuringEvent = false;
        public boolean bossbar = true;
        /** Ankündigung so viele Minuten vor dem Start nach Öffnungszeiten. */
        public List<Integer> announceMinutes = new ArrayList<>(List.of(15, 5, 1));

        // Losbude
        public boolean lottery = true;
        /** Preis eines Loses in €. */
        public double ticketPrice = 5.0;
        public int maxTickets = 10;
        /** So viel Prozent des Topfs werden ausgeschüttet, der Rest wird vernichtet. */
        public double potPayoutPercent = 80.0;
        /** Aufteilung der Ausschüttung auf Platz 1, 2, 3 … in Prozent. */
        public List<Double> lotteryShares = new ArrayList<>(List.of(50.0, 30.0, 20.0));

        // Geschenkesuche
        public double giftReward = 0.5;

        // Wichteln
        public boolean wichteln = true;

        // Adventskalender (1.–24. Dezember)
        public boolean advent = true;
        public List<AdventDay> adventDays = new ArrayList<>();

        // Wettbewerb
        public boolean vote = true;
        /** Preise in Coins für Platz 1, 2, 3 in jeder Kategorie. */
        public List<Double> prizes = new ArrayList<>(List.of(10.0, 5.0, 2.5));

        // Atmosphäre
        /** TwoGG-Atmos-Preset für Spieler im Marktgelände während des Events; leer = aus. */
        public String atmosPreset = "draussen_klar";
        public boolean music = true;
        public float musicVolume = 0.6f;
        /** Pause zwischen zwei Liedern in Sekunden. */
        public int musicPauseSeconds = 40;
        /** Tempo: Ticks pro Achtelnote. */
        public int musicTicksPerEighth = 4;
        public boolean weatherSnow = true;
        public boolean fireworks = true;
    }

    public static final class AdventDay {
        public int day;
        public double coins;
        public String item = "";
        public int count = 1;
        public String text = "";

        public AdventDay() {}

        public AdventDay(int day, double coins, String item, int count, String text) {
            this.day = day;
            this.coins = coins;
            this.item = item;
            this.count = count;
            this.text = text;
        }
    }

    /** Vorlage mit Weihnachtsmarkt-Werten für Winterland (Kurs 10 €: 1 WLC ≈ 10 €, Stundenlohn 1,5 WLC). */
    public static MarktConfig defaults() {
        MarktConfig c = new MarktConfig();
        List<BuyerItem> b = c.buyer.items;
        // Fichtenholz, Kohle, Wolle verkauft auch das Materiallager (beispiele/materiallager.json): Ankauf je Stück
        // höchstens dessen niedrigster Verkaufspreis, sonst lohnt „dort kaufen, hier abgeben“ (seit 0.5.2)
        b.add(new BuyerItem("minecraft:spruce_log", "Fichtenholz", 8, 0.1, 64, 32));
        b.add(new BuyerItem("minecraft:coal", "Kohle", 8, 0.1, 32, 16));
        b.add(new BuyerItem("minecraft:cookie", "Lebkuchen", 4, 0.2, 48, 24));
        b.add(new BuyerItem("minecraft:bread", "Brot", 2, 0.2, 32, 16));
        b.add(new BuyerItem("minecraft:pumpkin_pie", "Kürbiskuchen", 1, 0.3, 24, 12));
        b.add(new BuyerItem("minecraft:sweet_berries", "Süßbeeren", 8, 0.2, 48, 24));
        b.add(new BuyerItem("minecraft:apple", "Äpfel", 4, 0.3, 32, 16));
        b.add(new BuyerItem("minecraft:cod", "Kabeljau", 2, 0.3, 32, 16));
        b.add(new BuyerItem("minecraft:salmon", "Lachs", 2, 0.4, 24, 12));
        b.add(new BuyerItem("minecraft:white_wool", "Wolle", 16, 0.3, 8, 4));
        b.add(new BuyerItem("minecraft:honey_bottle", "Honig", 1, 0.4, 16, 8));
        b.add(new BuyerItem("minecraft:candle", "Kerzen", 2, 0.4, 16, 8));
        b.add(new BuyerItem("minecraft:iron_ingot", "Eisenbarren", 1, 0.3, 32, 16));
        b.add(new BuyerItem("minecraft:ice", "Eisblöcke", 8, 0.2, 32, 16));

        List<JobTemplate> j = c.jobs.pool;
        j.add(new JobTemplate("Brennholz für die Glühweinbude", "minecraft:spruce_log", 32, 2.0));
        j.add(new JobTemplate("Lebkuchen für den Süßwarenstand", "minecraft:cookie", 24, 2.0));
        j.add(new JobTemplate("Kürbiskuchen für das Markt-Café", "minecraft:pumpkin_pie", 6, 2.0));
        j.add(new JobTemplate("Tannengrün für die Dekoration", "minecraft:spruce_leaves", 32, 1.0));
        j.add(new JobTemplate("Laternen für die Gassen", "minecraft:lantern", 4, 2.0));
        j.add(new JobTemplate("Kerzen für die Krippe", "minecraft:candle", 8, 2.0));
        j.add(new JobTemplate("Wolle für Mützen und Schals", "minecraft:white_wool", 16, 1.5));
        j.add(new JobTemplate("Fisch für die Fischbude", "minecraft:cod", 10, 1.5));
        j.add(new JobTemplate("Glasflaschen für den Punsch", "minecraft:glass_bottle", 16, 1.0));
        j.add(new JobTemplate("Süßbeeren für die Marmelade", "minecraft:sweet_berries", 32, 1.5));
        j.add(new JobTemplate("Schneebälle für die Wurfbude", "minecraft:snowball", 64, 0.5));
        j.add(new JobTemplate("Eisblöcke für die Eisbahn", "minecraft:ice", 24, 1.5));
        j.add(new JobTemplate("Honig für den Met", "minecraft:honey_bottle", 4, 2.0));
        j.add(new JobTemplate("Papier für Geschenke", "minecraft:paper", 24, 1.0));
        j.add(new JobTemplate("Äpfel für Bratäpfel", "minecraft:apple", 12, 1.5));
        j.add(new JobTemplate("Kohle für die Maronenröster", "minecraft:charcoal", 16, 1.5));

        List<AdventDay> a = c.event.adventDays;
        String[][] items = {{"minecraft:cookie", "8"}, {"minecraft:candle", "2"}, {"minecraft:pumpkin_pie", "2"}, {"minecraft:lantern", "2"},
                {"minecraft:honey_bottle", "1"}, {"minecraft:sweet_berries", "16"}, {"minecraft:firework_rocket", "4"}, {"minecraft:cake", "1"}};
        for (int d = 1; d <= 24; d++) {
            if (d == 6) a.add(new AdventDay(d, 3.0, "minecraft:cookie", 12, "Nikolaus!"));
            else if (d == 24) a.add(new AdventDay(d, 10.0, "minecraft:cake", 1, "Frohe Weihnachten!"));
            else if (d % 3 == 0) a.add(new AdventDay(d, 1.5, "", 0, ""));
            else {
                String[] it = items[d % items.length];
                a.add(new AdventDay(d, 0.5, it[0], Integer.parseInt(it[1]), ""));
            }
        }
        return c;
    }

    /** Offensichtliche Fehler reparieren (nach dem Laden). */
    public void sanitize() {
        if (stalls == null) stalls = new Stalls();
        if (shop == null) shop = new Shop();
        if (buyer == null) buyer = new Buyer();
        if (jobs == null) jobs = new Jobs();
        if (snow == null) snow = new Snow();
        if (event == null) event = new Event();
        if (guide == null) guide = new Guide();
        Event e = event;
        if (e.schedule == null) e.schedule = new ArrayList<>();
        if (e.announceMinutes == null) e.announceMinutes = new ArrayList<>();
        if (e.lotteryShares == null || e.lotteryShares.isEmpty()) e.lotteryShares = new ArrayList<>(List.of(100.0));
        if (e.prizes == null) e.prizes = new ArrayList<>();
        if (e.adventDays == null) e.adventDays = new ArrayList<>();
        e.adventDays.removeIf(d -> d == null || d.day < 1 || d.day > 24);
        for (AdventDay d : e.adventDays) {
            if (d.item == null) d.item = "";
            if (d.text == null) d.text = "";
            d.coins = Math.max(0, d.coins);
            d.count = Math.max(0, Math.min(64 * 9, d.count));
        }
        if (e.atmosPreset == null) e.atmosPreset = "";
        e.defaultMinutes = Math.max(1, e.defaultMinutes);
        e.openingSeconds = Math.max(0, e.openingSeconds);
        e.finaleSeconds = Math.max(5, e.finaleSeconds);
        e.ticketPrice = Math.max(0, e.ticketPrice);
        e.maxTickets = Math.max(1, e.maxTickets);
        e.potPayoutPercent = clamp(e.potPayoutPercent, 0, 100);
        e.giftReward = Math.max(0, e.giftReward);
        e.musicVolume = (float) clamp(e.musicVolume, 0, 4);
        e.musicPauseSeconds = Math.max(0, e.musicPauseSeconds);
        e.musicTicksPerEighth = Math.max(1, Math.min(20, e.musicTicksPerEighth));
        if (title == null || title.isBlank()) title = "Weihnachtsmarkt";
        if (subtitle == null) subtitle = "";
        if (accent == null) accent = "#EE5A4F";
        stalls.periodMinutes = Math.max(1, stalls.periodMinutes);
        if (stalls.periodName == null || stalls.periodName.isBlank()) stalls.periodName = "Tag";
        if (stalls.periodNamePlural == null || stalls.periodNamePlural.isBlank()) stalls.periodNamePlural = stalls.periodName;
        stalls.maxPerPlayer = Math.max(0, stalls.maxPerPlayer);
        stalls.maxHelpers = Math.max(0, stalls.maxHelpers);
        stalls.buybackPercent = clamp(stalls.buybackPercent, 0, 100);
        stalls.rentRefundPercent = clamp(stalls.rentRefundPercent, 0, 100);
        stalls.maxVolume = Math.max(1, stalls.maxVolume);
        shop.feePercent = clamp(shop.feePercent, 0, 100);
        shop.maxOffers = Math.max(1, Math.min(48, shop.maxOffers));
        shop.maxPrice = Math.max(0.1, shop.maxPrice);
        shop.counterPrice = Math.max(0, shop.counterPrice);
        buyer.recoverMinutes = Math.max(1, buyer.recoverMinutes);
        buyer.minFactor = clamp(buyer.minFactor, 0, 1);
        if (buyer.items == null) buyer.items = new ArrayList<>();
        buyer.items.removeIf(i -> i == null || i.item == null || i.item.isBlank());
        for (BuyerItem i : buyer.items) {
            if (i.name == null) i.name = "";
            i.bundle = Math.max(1, Math.min(64 * 36, i.bundle));
            i.price = Math.max(0, i.price);
            i.demand = Math.max(1, i.demand);
            i.dailyLimit = Math.max(0, i.dailyLimit);
        }
        jobs.active = Math.max(0, Math.min(12, jobs.active));
        jobs.slots = Math.max(1, jobs.slots);
        jobs.refreshMinutes = Math.max(1, jobs.refreshMinutes);
        jobs.maxPerPlayer = Math.max(0, jobs.maxPerPlayer);
        jobs.playerJobHours = Math.max(1, jobs.playerJobHours);
        jobs.maxReward = Math.max(0.1, jobs.maxReward);
        if (jobs.pool == null) jobs.pool = new ArrayList<>();
        jobs.pool.removeIf(t -> t == null || t.item == null || t.item.isBlank());
        for (JobTemplate t : jobs.pool) {
            if (t.title == null || t.title.isBlank()) t.title = "Lieferung";
            t.count = Math.max(1, Math.min(64 * 36, t.count));
            t.reward = Math.max(0.1, t.reward);
            t.weight = Math.max(1, t.weight);
        }
        snow.blocksPerPay = Math.max(1, snow.blocksPerPay);
        snow.pay = Math.max(0, snow.pay);
        snow.dailyMax = Math.max(0, snow.dailyMax);
    }

    public AdventDay advent(int day) {
        for (AdventDay d : event.adventDays) if (d.day == day) return d;
        return null;
    }

    public BuyerItem buyerItem(String item) {
        for (BuyerItem i : buyer.items) if (i.item.equals(item)) return i;
        return null;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
