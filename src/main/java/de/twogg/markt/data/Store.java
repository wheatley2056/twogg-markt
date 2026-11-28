package de.twogg.markt.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Dateien des Markts: {@code config/twogg-markt/markt.json} (Einstellungen, Ankauf, Auftrags-Pool) und
 * {@code <welt>/twogg-markt/markt.json} (Stände, Aufträge, Zähler). Geschrieben wird atomar (.tmp, dann umbenennen).
 */
public final class Store {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private Store() {}

    public static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("twogg-markt").resolve("markt.json");
    }

    /** Einstellungen laden; fehlt die Datei, wird die Weihnachtsmarkt-Vorlage geschrieben. Bei Fehlern: null. */
    public static MarktConfig loadConfig() {
        Path f = configFile();
        try {
            if (!Files.exists(f)) {
                MarktConfig c = MarktConfig.defaults();
                saveConfig(c);
                return c;
            }
            MarktConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), MarktConfig.class);
            if (c == null) throw new IOException("leer");
            // Seit 0.6.0: Datei von vor 0.6.0 einmal hochziehen (Brandts Lager, Auftrags-Pool), Original als Kopie behalten
            java.util.List<String> migrated = c.migrate();
            c.sanitize();
            if (!migrated.isEmpty()) {
                keepCopy(f, MIGRATION_SUFFIX);
                saveConfig(c);
                for (String m : migrated) TwoggMarkt.LOGGER.info("Markt-Einstellungen: {}", m);
            }
            return c;
        } catch (IOException | RuntimeException e) {
            TwoggMarkt.LOGGER.error("config/twogg-markt/markt.json ist fehlerhaft ({}). Es gelten die Standardwerte, die Datei bleibt unverändert.", e.toString());
            backupBroken(f);
            return null;
        }
    }

    /**
     * Kopie einer fehlerhaften Datei ablegen ({@code markt.json.broken-<Zeit>}). Speichert danach jemand im Admin-Fenster,
     * überschreibt das die Datei mit den Standardwerten – die Kopie hält die Handarbeit fest.
     */
    static Path backupBroken(Path f) {
        try {
            if (!Files.exists(f)) return null;
            Path b = f.resolveSibling(f.getFileName() + ".broken-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
            Files.copy(f, b, StandardCopyOption.REPLACE_EXISTING);
            TwoggMarkt.LOGGER.warn("Kopie der fehlerhaften Datei: {}", b.getFileName());
            return b;
        } catch (IOException e) {
            TwoggMarkt.LOGGER.warn("Kopie von {} nicht angelegt: {}", f.getFileName(), e.toString());
            return null;
        }
    }

    /** Endung der Kopie vor der einmaligen Umstellung auf 0.6.0 ({@code markt.json.vor-0.6.0}). */
    public static final String MIGRATION_SUFFIX = ".vor-0.6.0";

    /** Kopie {@code <datei><suffix>} anlegen, wenn es sie noch nicht gibt (eine ältere Kopie wird nie überschrieben). */
    public static void keepCopy(Path f, String suffix) {
        try {
            Path b = f.resolveSibling(f.getFileName() + suffix);
            if (Files.exists(f) && !Files.exists(b)) Files.copy(f, b);
        } catch (IOException e) {
            TwoggMarkt.LOGGER.warn("Kopie von {} nicht angelegt: {}", f.getFileName(), e.toString());
        }
    }

    public static void saveConfig(MarktConfig c) {
        try {
            Files.createDirectories(configFile().getParent());
            write(configFile(), GSON.toJson(c));
        } catch (IOException | RuntimeException e) {
            // RuntimeException: z. B. NaN in einem Feld (Gson schreibt das nicht) – nicht den Server-Tick abbrechen (seit 0.5.2)
            TwoggMarkt.LOGGER.error("Einstellungen nicht gespeichert: {}", e.toString());
        }
    }

    public static MarktData loadData(Path dir) {
        Path f = dir.resolve("markt.json");
        if (!Files.exists(f)) {
            MarktData fresh = new MarktData();
            fresh.version = MarktData.VERSION;   // neue Welt: nichts umzustellen
            return fresh;
        }
        try {
            MarktData d = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), MarktData.class);
            if (d == null) throw new IOException("leer");
            d.sanitize();
            return d;
        } catch (IOException | RuntimeException e) {
            // Nicht überschreiben: wegsichern, damit nichts verloren geht
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            Path broken = dir.resolve("markt.json.broken-" + stamp);
            try {
                Files.move(f, broken);
            } catch (IOException ignored) {
            }
            TwoggMarkt.LOGGER.error("""

                    ==============================================================
                    TwoGG Markt: {} war nicht lesbar ({}).
                    Gesichert als {}. Der Markt startet LEER.
                    Server stoppen, Datei reparieren, als markt.json zurücklegen.
                    ==============================================================""", f, e.toString(), broken.getFileName());
            return new MarktData();
        }
    }

    public static void saveData(Path dir, MarktData d) {
        try {
            Files.createDirectories(dir);
            write(dir.resolve("markt.json"), GSON.toJson(d));
        } catch (IOException | RuntimeException e) {
            // RuntimeException: z. B. NaN in einem Feld (Gson schreibt das nicht) – nicht den Server-Tick abbrechen (seit 0.5.2)
            TwoggMarkt.LOGGER.error("Marktdaten nicht gespeichert: {}", e.toString());
        }
    }

    public static void write(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
