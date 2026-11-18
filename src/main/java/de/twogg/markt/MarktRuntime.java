package de.twogg.markt;

import de.twogg.economy.api.Economy;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.data.Store;
import de.twogg.markt.server.BoardService;
import de.twogg.markt.server.BuyerService;
import de.twogg.markt.server.EventService;
import de.twogg.markt.server.Holograms;
import de.twogg.markt.server.Outlines;
import de.twogg.markt.server.StallService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.time.LocalDate;

/** Laufender Zustand des Markts: Einstellungen, Weltdaten, Takt, Speichern. Alles im Server-Thread. */
public final class MarktRuntime {
    private static MinecraftServer server;
    private static MarktConfig config = MarktConfig.defaults();
    private static MarktData data = new MarktData();
    private static boolean dirty;
    private static boolean saveSoon;
    private static long ticks;

    private MarktRuntime() {}

    public static void start(MinecraftServer s) {
        server = s;
        MarktConfig c = Store.loadConfig();
        config = c == null ? MarktConfig.defaults() : c;
        data = Store.loadData(dir());
        rollDay();
        BoardService.refreshIfDue(true);
        TwoggMarkt.LOGGER.info("Markt geöffnet: {} Stände, {} Räumzonen, {} Aufträge", data.plots.size(), data.zones.size(), data.jobs.size());
    }

    public static void stop() {
        save();
        server = null;
    }

    public static boolean running() {
        return server != null;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static MarktConfig config() {
        return config;
    }

    public static MarktData data() {
        return data;
    }

    public static Path dir() {
        return server.getWorldPath(LevelResource.ROOT).resolve("twogg-markt");
    }

    public static void markDirty() {
        dirty = true;
    }

    /**
     * Nach einer Auszahlung: am Ende des Ticks speichern. Sonst stünde nach einem Absturz die Kasse (oder der Auftrag)
     * wieder im alten Zustand, während das Geld schon bei der Bank liegt – und würde ein zweites Mal ausgezahlt.
     */
    public static void saveSoon() {
        dirty = true;
        saveSoon = true;
    }

    public static void save() {
        if (server == null) return;
        Store.saveData(dir(), data);
        dirty = false;
        saveSoon = false;
    }

    /** Einstellungen neu einlesen. false, wenn die Datei fehlerhaft ist (dann bleibt alles, wie es war). */
    public static boolean reload() {
        MarktConfig c = Store.loadConfig();
        if (c == null) return false;
        config = c;
        return true;
    }

    /** Einstellungen ändern (Admin-Fenster) und schreiben. */
    public static void saveConfig() {
        config.sanitize();
        Store.saveConfig(config);
    }

    public static void tick() {
        if (server == null) return;
        ticks++;
        EventService.musicTick();
        if (ticks % 10 == 0) Outlines.tick(server);
        if (ticks % 20 == 0) EventService.tick();
        if (ticks % 100 == 0) {
            rollDay();
            StallService.checkExpiry();
            BoardService.refreshIfDue(false);
            BoardService.expirePlayerJobs();
            BuyerService.decay();
            Holograms.tick();
        }
        if (saveSoon || (dirty && ticks % 600 == 0)) save();
    }

    private static void rollDay() {
        String today = LocalDate.now().toString();
        if (!today.equals(data.day)) {
            data.day = today;
            data.daily.clear();
            dirty = true;
        }
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static long periodMs() {
        return config.stalls.periodMinutes * MarketMath.MINUTE;
    }

    public static double rate() {
        return Economy.available() ? Economy.rate() : 10.0;
    }

    public static String fmt(long tenths) {
        return Economy.available() ? Economy.format(tenths) : MarketMath.coins(tenths) + " Coins";
    }

    public static String unit() {
        try {
            String one = Economy.format(10);
            int i = one.indexOf(' ');
            return i < 0 ? "" : one.substring(i + 1);
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static int accent() {
        try {
            return 0xFF000000 | Integer.parseInt(config.accent.replace("#", "").trim(), 16);
        } catch (RuntimeException e) {
            return 0xFFEE5A4F;
        }
    }

    public static String periods(int n) {
        return n + " " + (n == 1 ? config.stalls.periodName : config.stalls.periodNamePlural);
    }
}
