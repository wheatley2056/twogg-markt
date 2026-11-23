package de.twogg.markt.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON-Datenklassen für die Fenster. Ein {@link View} für alle Fenster, {@code screen} sagt welches:
 * {@code market} (Stände, /markt), {@code shop} (Verkaufstheke), {@code buyer} (Ankauf), {@code board} (Auftragsbrett),
 * {@code admin} (Marktverwaltung). Beträge in Zehntel-Coins.
 */
public final class Views {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private Views() {}

    public static final class View {
        public String screen = "";
        /** Sitzungsnummer; der Client schickt sie bei „close“ zurück. */
        public int session;
        public String title = "";
        public String subtitle = "";
        public int accent = 0xFFEE5A4F;
        public String unit = "";
        public String reference = "€";
        public double rate = 10;
        public long balance;
        public long cash;
        public boolean hasAccount;
        public boolean allowCash = true;
        public String message = "";
        public boolean error;
        /** Fensterseite, die der Server vorschlägt (z. B. nach dem Anmieten „Mein Stand“). Leer = bleiben. */
        public String page = "";
        public String periodName = "Tag";
        public String periodNamePlural = "Tage";
        /** Markt-Handbuch (seit 0.3.0): Werte für die Platzhalter im Text. */
        public java.util.Map<String, String> guide;

        // market + admin
        public List<PlotLine> plots = new ArrayList<>();
        /** Stand, an dessen Schild man gerade steht (vorausgewählt). */
        public String focus = "";
        public int maxPerPlayer;
        public int ownedCount;
        public long pending;

        // shop
        public PlotLine stall;
        public boolean owner;
        public boolean member;
        public double feePercent;
        public List<OfferLine> offers = new ArrayList<>();
        public String hand = "";
        public String handName = "";
        public long maxPrice;
        public int maxOffers;
        public long counterPrice;

        // buyer
        public List<BuyerLine> buyerItems = new ArrayList<>();
        public boolean payCash;

        // board
        public List<JobLine> jobs = new ArrayList<>();
        public List<String> pickup = new ArrayList<>();
        public int pickupCount;
        public boolean playerJobs;
        public int myJobs;
        public int maxJobs;
        public long maxReward;
        public long jobRefreshIn;

        // admin
        public boolean allowed = true;
        public Selection selection;
        public List<ZoneLine> zones = new ArrayList<>();
        public Settings settings;

        // Event (Standübersicht, Losbude, Wichteln, Admin) und Adventskalender
        public EventInfo event;
        public List<AdventDoor> advent = new ArrayList<>();
        public int adventToday;
    }

    public static final class EventInfo {
        public String phase = "CLOSED";
        public String status = "";
        public long remaining, total;
        public int round;
        public List<String> schedule = new ArrayList<>();
        public String next = "";
        public boolean lottery, wichteln, vote, advent;
        public int myTickets, maxTickets, ticketsTotal, players;
        public long ticketPrice, pot, potPayout;
        public String myVote = "";
        public List<Board> boards = new ArrayList<>();
        public List<String> lastResults = new ArrayList<>();
        public String wichtelGift = "";
        public int wichtelCount;
        public String area = "";
        public String leaderboard = "";
        public String atmosPreset = "";
        public boolean atmosLoaded, music, bossbar, onlyDuringEvent, weatherSnow, fireworks;
        public int defaultMinutes;
        public long giftReward;
    }

    public static final class Board {
        public String title = "";
        public List<String> lines = new ArrayList<>();
    }

    public static final class AdventDoor {
        public int day;
        /** OPEN (heute, noch zu), DONE (schon geöffnet), MISSED (vorbei), LOCKED (kommt noch). */
        public String state = "LOCKED";
        public String text = "";
        public String item = "";
    }

    public static final class PlotLine {
        public String id = "", name = "", note = "", status = "", mode = "";
        public String owner = "", ownerName = "";
        public String size = "", pos = "", dim = "";
        public int area;
        public long rentTenths, buyTenths;
        public double rentEuro, buyEuro;
        public int maxPeriods, bookable;
        public long remaining;
        public boolean mine, helper;
        public List<String> helpers = new ArrayList<>();
        public long till, salesTotal;
        public int offers;
        public boolean shopOpen;
        public long refund;
        public boolean hasSnapshot;
    }

    public static final class OfferLine {
        public String id = "";
        public String item = "";
        public String name = "";
        public int count;
        public long price;
        public int stock;
        public long sold;
        /** Laden-Ware (seit 0.5.0): Zusatzzeile, z. B. „Lager 12 Stacks“; leer = Standard. */
        public String note = "";
    }

    public static final class BuyerLine {
        public String item = "", name = "";
        public int bundle;
        public long price, basePrice;
        public int have;
        public int left;
        public int demand;
        public double sold;
        /** Laden-Ankauf (seit 0.4.0): ID der Zeile; leer = Ankaufstelle (Item-ID aus {@link #item}). */
        public String id = "";
        /** Laden-Ware (seit 0.5.0): Zusatzzeile statt „heute noch …“, z. B. „Lager fast voll“. */
        public String note = "";
    }

    public static final class JobLine {
        public String id = "", title = "", item = "", itemName = "";
        public int count;
        public long reward;
        public int remaining, slots;
        public boolean system, mine, done;
        public String poster = "";
        public long expiresIn;
        public int have;
    }

    public static final class ZoneLine {
        public String id = "", name = "", size = "", pos = "";
    }

    public static final class Selection {
        public String a = "", b = "", size = "";
        public boolean complete;
        public long volume;
        public String overlap = "";
    }

    public static final class Settings {
        public int periodMinutes, maxPerPlayer, maxHelpers, maxOffers;
        public double feePercent, buybackPercent, rentRefundPercent, counterPrice;
        public boolean holograms, resetOnRelease, protectFree, allowCash, buyerEnabled, jobsEnabled, playerJobs, snowEnabled, buyerPayCash;
        public int jobsActive, jobsSlots;
        public double snowPay, snowDailyMax;
        public int snowBlocksPerPay;
    }

    /** Wunsch vom Client. Nicht jedes Feld gilt für jede Aktion. */
    public static final class Action {
        public String a;
        public String screen;
        public String plot;
        public String id;
        public String text;
        public String name;
        public int count;
        public long amount;
        public double value;
        public boolean cash;
        public boolean flag;
        public int session;

        public static Action of(String a) {
            Action x = new Action();
            x.a = a;
            return x;
        }
    }
}
