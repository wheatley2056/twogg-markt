package de.twogg.markt.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen des Markts ({@code config/twogg-markt/markt.json}). Beträge in Coins mit höchstens einer
 * Nachkommastelle, Preise für Stände in der Referenzwährung (€) des Economy-Profils.
 */
public final class MarktConfig {
    /** Stand der Einstellungsdatei: 0 = vor 0.6.0 (Feld fehlt), 2 = seit 0.6.0. Siehe {@link #migrate()}. */
    public static final int VERSION = 2;
    public int configVersion;

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
    /**
     * Brandts Lager (seit 0.6.0): Waren mit begrenztem Bestand im Laden {@link Lager#laden}, jede Woche aufgefüllt.
     * {@code null} nur in Dateien von vor 0.6.0 – {@link #migrate()} setzt dann die Vorlage ein.
     */
    public Lager lager;

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
        /**
         * Seit 0.6.0: kompletter Stapel (ItemStack-JSON mit Anzahl und Daten wie Verzauberungen oder Namen), gesetzt per
         * {@code /markt advent item <tag>} oder in der Marktverwaltung aus der Hand. Hat Vorrang vor {@link #item}/{@link #count}.
         */
        public JsonElement stack;

        public AdventDay() {}

        public AdventDay(int day, double coins, String item, int count, String text) {
            this.day = day;
            this.coins = coins;
            this.item = item;
            this.count = count;
            this.text = text;
        }
    }

    /**
     * Brandts Lager (seit 0.6.0): Der Laden {@link #laden} (Storyteller-NPC Brandt öffnet ihn mit
     * {@code markt laden oeffnen materiallager {player}}) führt genau diese Waren – kein unbegrenzter Vorrat. Beim Start und
     * bei {@code /markt reload} wird der Laden mit dieser Liste abgeglichen (Bestände bleiben erhalten).
     */
    public static final class Lager {
        public boolean enabled = true;
        /** ID des Ladens in den Weltdaten ({@code laeden}). */
        public String laden = "materiallager";
        /** Fenstertitel; leer = Name des Ladens bleibt. */
        public String name = "Materiallager";
        public String subtitle = "Brandts Lager";
        /** Nachschub: Wochentag (MONDAY … SUNDAY), Uhrzeit hh:mm und Zeitzone. Füllt bis zum Wochenbestand auf. */
        public String restockDay = "SUNDAY";
        public String restockTime = "06:00";
        public String zone = "Europe/Berlin";
        /** Überschuss (von Spielern über den Wochenbestand verkauft) beim Nachschub mitnehmen; Standard: bleibt liegen. */
        public boolean trimSurplus = false;
        /** Rückkauf = Verkaufspreis × spread (höchstens 0,95). */
        public double spread = 0.6;
        /** Lagerplatz = Wochenbestand × capacityFactor, wenn eine Ware keinen eigenen {@code capacity} hat. */
        public double capacityFactor = 2.0;
        public List<LagerItem> items = new ArrayList<>();
    }

    /** Eine Ware in Brandts Lager. Bestände in Stück, Preis in Coins je Einheit ({@code unit} Stück) beim Wochenbestand. */
    public static final class LagerItem {
        /** Item-ID (z. B. {@code minecraft:coal}); bei {@link #stack} nur zur Anzeige in Meldungen. */
        public String item = "";
        /** Optional: kompletter Stapel mit Daten (Verzauberungsbücher …); gehandelt wird dann nur genau dieses Item. */
        public JsonElement stack;
        /** Anzeigename; leer = Name des Items. */
        public String name = "";
        /** Stück pro Kauf/Verkauf. */
        public int unit = 1;
        /**
         * Coins je Einheit, die die erste Einheit kostet, wenn der Bestand beim Wochenbestand steht (so steht es dann im
         * Fenster). Leeres Lager etwa doppelt, volles Lager etwa halb so teuer. Siehe {@link #baseTenths(double)}.
         */
        public double price = 0.1;
        /** Wochenbestand (Soll) in Stück: so weit füllt der Nachschub jeden Sonntag auf. */
        public long weekly = 64;
        /** Lagerplatz in Stück (Obergrenze für Verkäufe der Spieler); 0 = weekly × capacityFactor. */
        public long capacity;

        public LagerItem() {}

        public LagerItem(String item, String name, int unit, double price, long weekly) {
            this.item = item;
            this.name = name;
            this.unit = unit;
            this.price = price;
            this.weekly = weekly;
        }

        /** Lagerplatz in Stück: eigener Wert oder Wochenbestand × Faktor, auf ganze Einheiten aufgerundet, mindestens eine Einheit über dem Soll. */
        public long capacity(double factor) {
            int u = Math.max(1, unit);
            long cap = capacity > 0 ? capacity : (long) Math.ceil(Math.max(0, weekly) * Math.max(1, factor));
            cap = Math.max(cap, Math.max(0, weekly) + u);
            return (cap + u - 1) / u * u;
        }

        /**
         * Grundpreis der Ware in Zehnteln (Preisfaktor 1 genau beim Soll), so gewählt, dass die erste Einheit beim
         * Wochenbestand höchstens {@link #price} kostet – Brandt rechnet je Einheit zum Bestand nach der Entnahme und rundet
         * auf, ohne diese Umrechnung stünde z. B. Bruchstein mit 0,5 statt 0,4 im Fenster.
         */
        public long baseTenths(double factor) {
            long want = Math.max(1, MarketMath.tenths(price));
            long cap = capacity(factor), target = Math.min(cap, Math.max(0, weekly));
            int u = Math.max(1, unit);
            double f = StockMath.factor(target - u, target, cap);
            long b = Math.max(1, (long) Math.floor(want / f));
            while (b > 1 && StockMath.nextSellPrice(b, target, target, cap, u) > want) b--;
            while (StockMath.nextSellPrice(b + 1, target, target, cap, u) <= want) b++;
            return b;
        }

        @Override
        public String toString() {
            return item + (stack == null ? "" : " " + stack);
        }
    }

    /** Verzauberungsbuch als Stapel-JSON (Datenkomponente {@code minecraft:stored_enchantments}). */
    static JsonElement book(String enchantment, int level) {
        return JsonParser.parseString("{\"id\":\"minecraft:enchanted_book\",\"count\":1,\"components\":"
                + "{\"minecraft:stored_enchantments\":{\"minecraft:" + enchantment + "\":" + level + "}}}");
    }

    private static LagerItem bookItem(String enchantment, int level, String name, double price, long weekly) {
        LagerItem i = new LagerItem("minecraft:enchanted_book", name, 1, price, weekly);
        i.stack = book(enchantment, level);
        return i;
    }

    /**
     * Vorlage für Brandts Lager (Verkaufsliste der Serverleitung, 0.6.0). Preisgefüge: Stundenlohn 1,5 WLC (Stechuhr),
     * Woche aktiv ≈ 30–40 WLC. Massengüter kosten Minuten, Seltenes (Reparatur, Schallplatten, Besätze, Drachenatem)
     * Stunden bis Tage Arbeit. Wochenbestand für 42 Spieler. Je Stück nie billiger, als die Ankaufstelle der Vorlage
     * für dasselbe (oder daraus geschmolzene) Item zahlt – sonst lohnt „bei Brandt kaufen, dort abgeben“ (Test).
     */
    public static Lager defaultLager() {
        Lager l = new Lager();
        List<LagerItem> w = l.items;
        // --- Items
        w.add(new LagerItem("minecraft:raw_iron", "Rohes Eisen", 8, 4.8, 640));
        w.add(new LagerItem("minecraft:raw_copper", "Rohes Kupfer", 16, 1.6, 1024));
        w.add(new LagerItem("minecraft:raw_gold", "Rohes Gold", 8, 6.4, 336));
        w.add(new LagerItem("autumnal_winterly:raw_platinum", "Rohes Platin", 4, 6.0, 168));
        w.add(new LagerItem("minecraft:emerald", "Smaragd", 1, 0.8, 256));
        w.add(new LagerItem("minecraft:redstone", "Redstone-Staub", 32, 1.2, 2048));
        w.add(new LagerItem("minecraft:lapis_lazuli", "Lapislazuli", 16, 1.2, 1024));
        w.add(new LagerItem("minecraft:coal", "Kohle", 32, 1.0, 2048));
        w.add(new LagerItem("minecraft:diamond", "Diamant", 1, 4.0, 84));
        w.add(new LagerItem("minecraft:string", "Faden", 16, 0.6, 1024));
        w.add(new LagerItem("minecraft:glowstone_dust", "Glowstone-Staub", 16, 1.2, 672));
        w.add(new LagerItem("minecraft:nether_wart", "Netherwarze", 16, 0.8, 672));
        w.add(new LagerItem("minecraft:magma_cream", "Magmacreme", 4, 1.2, 168));
        w.add(new LagerItem("minecraft:blaze_rod", "Lohenrute", 1, 0.8, 128));
        w.add(new LagerItem("minecraft:quartz", "Netherquarz", 32, 1.6, 1344));
        w.add(new LagerItem("minecraft:slime_ball", "Schleimball", 8, 1.2, 336));
        w.add(new LagerItem("minecraft:honeycomb", "Honigwabe", 8, 3.2, 336));
        w.add(new LagerItem("minecraft:amethyst_shard", "Amethystscherbe", 8, 0.8, 512));
        w.add(new LagerItem("minecraft:leather", "Leder", 8, 1.2, 512));
        w.add(new LagerItem("minecraft:feather", "Feder", 16, 0.6, 672));
        w.add(new LagerItem("minecraft:ink_sac", "Tintenbeutel", 8, 0.8, 336));
        w.add(new LagerItem("minecraft:ghast_tear", "Ghastträne", 1, 2.0, 32));
        w.add(new LagerItem("minecraft:dragon_breath", "Drachenatem", 1, 3.0, 32));
        w.add(new LagerItem("minecraft:dried_ghast", "Ausgetrockneter Ghast", 1, 8.0, 8));
        w.add(bookItem("mending", 1, "Verzauberungsbuch: Reparatur", 25.0, 4));
        w.add(bookItem("unbreaking", 3, "Verzauberungsbuch: Haltbarkeit III", 6.0, 8));
        w.add(bookItem("efficiency", 4, "Verzauberungsbuch: Effizienz IV", 5.0, 8));
        w.add(bookItem("swift_sneak", 3, "Verzauberungsbuch: Huschen III", 20.0, 2));
        w.add(new LagerItem("minecraft:music_disc_pigstep", "Schallplatte „Pigstep“", 1, 12.0, 2));
        w.add(new LagerItem("minecraft:music_disc_tears", "Schallplatte „Tears“", 1, 15.0, 2));
        w.add(new LagerItem("minecraft:rib_armor_trim_smithing_template", "Rüstungsbesatz „Rippe“", 1, 10.0, 2));
        w.add(new LagerItem("minecraft:snout_armor_trim_smithing_template", "Rüstungsbesatz „Schnauze“", 1, 10.0, 2));
        w.add(new LagerItem("minecraft:spire_armor_trim_smithing_template", "Rüstungsbesatz „Turmspitze“", 1, 15.0, 2));
        w.add(new LagerItem("minecraft:chorus_fruit", "Chorusfrucht", 16, 1.2, 672));
        w.add(new LagerItem("minecraft:shulker_shell", "Shulkerschale", 1, 4.0, 32));
        w.add(new LagerItem("minecraft:piglin_banner_pattern", "Bannervorlage „Schnauze“", 1, 4.0, 4));
        w.add(new LagerItem("minecraft:sugar_cane", "Zuckerrohr", 32, 0.6, 1344));
        String[][] dyes = {{"white", "Weißer"}, {"light_gray", "Hellgrauer"}, {"gray", "Grauer"}, {"black", "Schwarzer"},
                {"brown", "Brauner"}, {"red", "Roter"}, {"orange", "Oranger"}, {"yellow", "Gelber"}, {"lime", "Hellgrüner"},
                {"green", "Grüner"}, {"cyan", "Türkiser"}, {"light_blue", "Hellblauer"}, {"blue", "Blauer"},
                {"purple", "Violetter"}, {"magenta", "Magenta"}, {"pink", "Rosa"}};
        for (String[] d : dyes) w.add(new LagerItem("minecraft:" + d[0] + "_dye", d[1] + " Farbstoff", 16, 0.6, 672));
        w.add(new LagerItem("minecraft:pumpkin_seeds", "Kürbiskerne", 16, 0.3, 672));
        w.add(new LagerItem("minecraft:melon_seeds", "Melonenkerne", 16, 0.3, 672));
        w.add(new LagerItem("minecraft:beetroot_seeds", "Rote-Bete-Samen", 16, 0.3, 672));
        w.add(new LagerItem("autumnal_winterly:winterberry_seeds", "Winterbeeren-Samen", 16, 0.6, 672));
        w.add(new LagerItem("minecraft:bone", "Knochen", 16, 1.2, 672));
        w.add(new LagerItem("minecraft:lava_bucket", "Lavaeimer", 1, 1.5, 42));
        // --- Blöcke (je 64 Stück)
        w.add(new LagerItem("minecraft:cobblestone", "Bruchstein", 64, 0.4, 10752));
        w.add(new LagerItem("minecraft:gravel", "Kies", 64, 0.6, 4096));
        w.add(new LagerItem("minecraft:sand", "Sand", 64, 0.6, 6144));
        w.add(new LagerItem("minecraft:dirt", "Erde", 64, 0.3, 6144));
        w.add(new LagerItem("minecraft:blackstone", "Schwarzstein", 64, 0.8, 4096));
        w.add(new LagerItem("minecraft:netherrack", "Netherrack", 64, 0.3, 4096));
        w.add(new LagerItem("minecraft:basalt", "Basalt", 64, 0.8, 4096));
        w.add(new LagerItem("minecraft:tuff", "Tuffstein", 64, 0.6, 4096));
        w.add(new LagerItem("minecraft:andesite", "Andesit", 64, 0.5, 4096));
        w.add(new LagerItem("minecraft:diorite", "Diorit", 64, 0.5, 4096));
        w.add(new LagerItem("minecraft:granite", "Granit", 64, 0.5, 4096));
        String[][] trees = {{"minecraft:oak", "Eichen", "2.0", "4096"}, {"minecraft:spruce", "Fichten", "2.0", "4096"},
                {"minecraft:birch", "Birken", "2.0", "4096"}, {"minecraft:jungle", "Tropenbaum", "2.0", "2688"},
                {"minecraft:acacia", "Akazien", "2.0", "2688"}, {"minecraft:dark_oak", "Schwarzeichen", "2.0", "2688"},
                {"minecraft:mangrove", "Mangroven", "2.4", "2688"}, {"minecraft:cherry", "Kirsch", "2.4", "2688"},
                {"minecraft:pale_oak", "Blasseichen", "2.4", "2688"}, {"autumnal_winterly:fir", "Tannen", "2.4", "2688"},
                {"arts_and_crafts:cork", "Kork", "2.4", "2688"}};
        for (String[] t : trees) w.add(new LagerItem(t[0] + "_log", t[1] + "stamm", 64, Double.parseDouble(t[2]), Long.parseLong(t[3])));
        for (String[] t : trees) w.add(new LagerItem(t[0] + "_leaves", t[1] + "laub", 64, 0.6, 2688));
        w.add(new LagerItem("minecraft:azalea_leaves", "Azaleenlaub", 64, 0.6, 2688));
        w.add(new LagerItem("minecraft:flowering_azalea_leaves", "Blühendes Azaleenlaub", 64, 0.6, 2688));
        w.add(new LagerItem("minecraft:white_wool", "Weiße Wolle", 16, 0.8, 1024));
        w.add(new LagerItem("autumnal_winterly:kaolin", "Kaolin", 64, 0.8, 2688));
        w.add(new LagerItem("arts_and_crafts:gypsum", "Gips", 64, 0.8, 2688));
        w.add(new LagerItem("minecraft:calcite", "Calcit", 64, 1.0, 2688));
        w.add(new LagerItem("minecraft:clay", "Tonblock", 64, 1.6, 2688));
        w.add(new LagerItem("minecraft:terracotta", "Keramik", 64, 1.6, 2688));
        w.add(new LagerItem("arts_and_crafts:soapstone", "Speckstein", 64, 0.8, 2688));
        w.add(new LagerItem("minecraft:glass", "Glas", 64, 1.6, 4096));
        w.add(new LagerItem("minecraft:moss_block", "Moosblock", 64, 1.0, 2688));
        w.add(new LagerItem("minecraft:red_sand", "Roter Sand", 64, 0.8, 2688));
        w.add(new LagerItem("minecraft:soul_sand", "Seelensand", 64, 1.0, 2688));
        w.add(new LagerItem("minecraft:soul_soil", "Seelenerde", 64, 1.0, 2688));
        w.add(new LagerItem("minecraft:end_stone", "Endstein", 64, 1.6, 2688));
        return l;
    }

    /** Vorlage des Auftrags-Pools bis 0.5.2 (Beispielaufträge). Seit 0.6.0 startet das Brett leer; nur für die Migration. */
    public static List<JobTemplate> winterlandJobPool() {
        List<JobTemplate> j = new ArrayList<>();
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
        return j;
    }

    /** Stammt ein Auftrag (Titel + Item) aus der alten Beispiel-Vorlage? Selbst angelegte Vorlagen bleiben. */
    public static boolean isSampleJob(String title, String item) {
        for (JobTemplate t : winterlandJobPool()) if (t.title.equals(title) && t.item.equals(item)) return true;
        return false;
    }

    /**
     * Einstellungsdatei von vor 0.6.0 hochziehen (einmal, vor {@link #sanitize()}): Brandts Lager mit der Vorlage
     * anlegen, falls es fehlt, und die Beispielaufträge der alten Vorlage aus dem Pool nehmen (eigene bleiben).
     *
     * @return Meldungen fürs Log; leer = nichts zu tun
     */
    public List<String> migrate() {
        List<String> out = new ArrayList<>();
        if (configVersion >= VERSION) return out;
        if (lager == null) {
            lager = defaultLager();
            out.add("Brandts Lager eingerichtet: " + lager.items.size() + " Waren mit Wochenbestand, Nachschub jeden Sonntag "
                    + lager.restockTime + " (" + lager.zone + "), Laden „" + lager.laden + "“.");
        }
        if (jobs != null && jobs.pool != null) {
            int before = jobs.pool.size();
            jobs.pool.removeIf(t -> t != null && isSampleJob(t.title, t.item));
            int removed = before - jobs.pool.size();
            if (removed > 0) out.add(removed + " Beispielaufträge aus dem Auftrags-Pool entfernt (eigene Vorlagen: " + jobs.pool.size() + ").");
        }
        out.add("Einstellungen von Version " + configVersion + " auf " + VERSION + " gebracht.");
        configVersion = VERSION;
        return out;
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

        // Seit 0.6.0 ohne Beispielaufträge: Das Brett zeigt nur, was Admins in den Pool schreiben oder Spieler aushängen
        c.lager = defaultLager();
        c.configVersion = VERSION;

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
        if (lager == null) lager = new Lager();
        sanitizeLager(lager);
        Event e = event;
        if (e.schedule == null) e.schedule = new ArrayList<>();
        if (e.announceMinutes == null) e.announceMinutes = new ArrayList<>();
        if (e.lotteryShares == null || e.lotteryShares.isEmpty()) e.lotteryShares = new ArrayList<>(List.of(100.0));
        if (e.prizes == null) e.prizes = new ArrayList<>();
        if (e.adventDays == null) e.adventDays = new ArrayList<>();
        e.adventDays.removeIf(d -> d == null || d.day < 1 || d.day > 24);
        for (AdventDay d : e.adventDays) {
            if (d.stack != null && d.stack.isJsonNull()) d.stack = null;
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

    private static void sanitizeLager(Lager l) {
        if (l.laden == null || !l.laden.matches("[a-z0-9_]{1,32}")) l.laden = "materiallager";
        if (l.name == null) l.name = "";
        if (l.subtitle == null) l.subtitle = "";
        l.restockDay = Restock.day(l.restockDay == null ? "" : l.restockDay, java.time.DayOfWeek.SUNDAY).name();
        l.restockTime = Restock.time(l.restockTime == null ? "" : l.restockTime, java.time.LocalTime.of(6, 0)).toString();
        l.zone = Restock.zone(l.zone).getId();
        l.spread = StockMath.spread(l.spread);
        l.capacityFactor = Double.isFinite(l.capacityFactor) ? Math.max(1, Math.min(100, l.capacityFactor)) : 2.0;
        if (l.items == null) l.items = new ArrayList<>();
        l.items.removeIf(i -> i == null || ((i.item == null || i.item.isBlank()) && (i.stack == null || i.stack.isJsonNull())));
        for (LagerItem i : l.items) {
            if (i.item == null) i.item = "";
            if (i.name == null) i.name = "";
            if (i.stack != null && i.stack.isJsonNull()) i.stack = null;
            i.unit = Math.max(1, Math.min(64 * 9, i.unit));
            i.price = Double.isFinite(i.price) ? Math.max(0.1, Math.min(1_000_000, i.price)) : 0.1;
            i.weekly = Math.max(0, Math.min(10_000_000, i.weekly));
            i.capacity = Math.max(0, Math.min(20_000_000, i.capacity));
        }
    }

    public AdventDay advent(int day) {
        for (AdventDay d : event.adventDays) if (d.day == day) return d;
        return null;
    }

    /** Türchen zum Bearbeiten (seit 0.6.0): vorhandenes oder ein neues, leeres (1–24), sortiert eingefügt. */
    public AdventDay adventOrCreate(int day) {
        if (day < 1 || day > 24) throw new IllegalArgumentException("Türchen 1 bis 24.");
        AdventDay d = advent(day);
        if (d != null) return d;
        d = new AdventDay(day, 0, "", 0, "");
        event.adventDays.add(d);
        event.adventDays.sort(java.util.Comparator.comparingInt(x -> x.day));
        return d;
    }

    /** Türchen auf die Vorlage zurücksetzen (seit 0.6.0); {@code day = 0}: alle 24. */
    public void resetAdvent(int day) {
        List<AdventDay> tpl = defaults().event.adventDays;
        if (day == 0) {
            event.adventDays = tpl;
            return;
        }
        event.adventDays.removeIf(d -> d.day == day);
        for (AdventDay d : tpl) if (d.day == day) event.adventDays.add(d);
        event.adventDays.sort(java.util.Comparator.comparingInt(x -> x.day));
    }

    public BuyerItem buyerItem(String item) {
        for (BuyerItem i : buyer.items) if (i.item.equals(item)) return i;
        return null;
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
