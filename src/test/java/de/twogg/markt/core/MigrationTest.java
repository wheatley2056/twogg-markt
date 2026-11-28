package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Einmalige Umstellung auf 0.6.0: Einstellungen (Lager, Auftrags-Pool) und Weltdaten (Beispielaufträge, unbegrenzter Vorrat). */
class MigrationTest {
    private static final Gson GSON = new Gson();

    /** Wie die Datei auf dem Server (0.5.2): kein configVersion, kein lager, Pool der Vorlage – hier plus eine eigene Vorlage. */
    private static final String OLD_CONFIG = """
            {"title": "Weihnachtsmarkt",
             "jobs": {"enabled": true, "active": 4, "pool": [
               {"title": "Brennholz für die Glühweinbude", "item": "minecraft:spruce_log", "count": 32, "reward": 2.0, "weight": 1},
               {"title": "Schneebälle für die Wurfbude", "item": "minecraft:snowball", "count": 64, "reward": 0.5, "weight": 1},
               {"title": "Kohle für die Maronenröster", "item": "minecraft:charcoal", "count": 16, "reward": 1.5, "weight": 1},
               {"title": "Eis für Brandt", "item": "minecraft:ice", "count": 8, "reward": 1.0, "weight": 2}]},
             "event": {"adventDays": [{"day": 1, "coins": 0.5, "item": "minecraft:candle", "count": 2, "text": ""}]}}""";

    @Test
    void oldConfigGetsLagerAndLosesOnlySampleJobs() {
        MarktConfig c = GSON.fromJson(OLD_CONFIG, MarktConfig.class);
        assertEquals(0, c.configVersion);
        assertNull(c.lager);
        List<String> log = c.migrate();
        c.sanitize();
        assertEquals(MarktConfig.VERSION, c.configVersion);
        assertNotNull(c.lager);
        assertEquals(107, c.lager.items.size());
        assertEquals("materiallager", c.lager.laden);
        assertEquals("SUNDAY", c.lager.restockDay);
        assertEquals(1, c.jobs.pool.size(), "nur die eigene Vorlage bleibt");
        assertEquals("Eis für Brandt", c.jobs.pool.getFirst().title);
        assertEquals(1, c.event.adventDays.size(), "Adventskalender bleibt unangetastet");
        assertTrue(log.stream().anyMatch(l -> l.contains("3 Beispielaufträge")), log.toString());
        // zweites Mal: nichts mehr zu tun
        assertTrue(c.migrate().isEmpty());
        // Speichern und wieder laden: bleibt bei Version 2, Lager bleibt
        MarktConfig again = GSON.fromJson(GSON.toJson(c), MarktConfig.class);
        assertTrue(again.migrate().isEmpty());
        assertEquals(107, again.lager.items.size());
    }

    @Test
    void ownLagerInAnOldFileIsKept() {
        MarktConfig c = GSON.fromJson("{\"lager\": {\"laden\": \"brandt\", \"items\": [{\"item\": \"minecraft:coal\", \"price\": 1}]}}", MarktConfig.class);
        c.migrate();
        c.sanitize();
        assertEquals("brandt", c.lager.laden);
        assertEquals(1, c.lager.items.size());
    }

    @Test
    void freshDefaultsNeedNoMigrationAndHaveNoSampleJobs() {
        MarktConfig c = MarktConfig.defaults();
        assertEquals(MarktConfig.VERSION, c.configVersion);
        assertTrue(c.jobs.pool.isEmpty(), "Auftragsbrett startet leer");
        assertTrue(c.migrate().isEmpty());
        assertEquals(16, MarktConfig.winterlandJobPool().size());
        assertTrue(MarktConfig.isSampleJob("Fisch für die Fischbude", "minecraft:cod"));
        assertFalse(MarktConfig.isSampleJob("Fisch für die Fischbude", "minecraft:salmon"));
    }

    /** Weltdaten wie auf dem Server: vier Marktaufträge aus der Vorlage, dazu hier ein Spieler- und ein eigener Marktauftrag. */
    private static final String OLD_DATA = """
            {"version": 1,
             "laeden": {"materiallager": {"id": "materiallager", "name": "Materiallager",
                 "sells": [{"id": "v1", "name": "Fackel", "count": 4, "price": 5}],
                 "buys": [{"id": "a2", "name": "Kohle", "count": 8, "price": 12, "dailyLimit": 2}],
                 "waren": [{"id": "w3", "name": "Bruchstein", "unit": 64, "base": 4, "capacity": 128, "target": 64, "stock": 64}],
                 "nextOffer": 4},
               "anderer": {"id": "anderer", "name": "Kiosk", "sells": [{"id": "v1", "name": "Brot", "count": 1, "price": 2}]}},
             "jobs": [
               {"id": "m44", "system": true, "title": "Schneebälle für die Wurfbude", "item": "minecraft:snowball", "count": 64, "reward": 5, "slots": 3, "poster": ""},
               {"id": "m43", "system": true, "title": "Kohle für die Maronenröster", "item": "minecraft:charcoal", "count": 16, "reward": 15, "slots": 3},
               {"id": "m42", "system": true, "title": "Fisch für die Fischbude", "item": "minecraft:cod", "count": 10, "reward": 15, "slots": 3, "doneBy": ["u1"]},
               {"id": "m41", "system": true, "title": "Eisblöcke für die Eisbahn", "item": "minecraft:ice", "count": 24, "reward": 15, "slots": 3},
               {"id": "m40", "system": true, "title": "Eis für Brandt", "item": "minecraft:ice", "count": 8, "reward": 10, "slots": 3},
               {"id": "s7", "system": false, "title": "Fisch für die Fischbude", "item": "minecraft:cod", "count": 10, "reward": 30, "slots": 1,
                "poster": "u2", "posterName": "Anna"}]}""";

    @Test
    void oldDataLosesSampleJobsAndInfiniteStockOnly() {
        MarktData d = GSON.fromJson(OLD_DATA, MarktData.class);
        d.sanitize();
        MarktConfig c = MarktConfig.defaults();
        List<String> log = d.migrate(c);
        assertEquals(MarktData.VERSION, d.version);
        assertEquals(List.of("m40", "s7"), d.jobs.stream().map(j -> j.id).toList(), "eigener Marktauftrag und Spielerauftrag bleiben");
        Laden l = d.laeden.get("materiallager");
        assertTrue(l.sells.isEmpty(), "kein unbegrenzter Vorrat mehr");
        assertTrue(l.buys.isEmpty(), "kein fester Ankauf mehr");
        assertEquals(1, l.waren.size(), "Waren mit Bestand bleiben (Abgleich übernimmt sie)");
        assertEquals(1, d.laeden.get("anderer").sells.size(), "andere Läden bleiben");
        assertTrue(l.trimSurplus, "älterer Laden: Standard wie bisher (Abgleich setzt es aus der Config)");
        assertTrue(log.stream().anyMatch(x -> x.contains("4 Beispielaufträge")), log.toString());
        assertTrue(log.stream().anyMatch(x -> x.contains("1 Verkaufszeilen")), log.toString());
        assertTrue(d.migrate(c).isEmpty(), "nur einmal");
    }

    @Test
    void disabledLagerKeepsTheShop() {
        MarktData d = GSON.fromJson(OLD_DATA, MarktData.class);
        d.sanitize();
        MarktConfig c = MarktConfig.defaults();
        c.lager.enabled = false;
        d.migrate(c);
        assertEquals(1, d.laeden.get("materiallager").sells.size());
        assertEquals(2, d.jobs.size(), "Beispielaufträge gehen trotzdem");
    }
}
