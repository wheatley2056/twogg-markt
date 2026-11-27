package de.twogg.markt.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StoreTest {

    @Test
    void kaputteEinstellungenWerdenVorDemUeberschreibenGesichert(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("markt.json");
        Files.writeString(f, "{ \"stalls\": { kaputt", StandardCharsets.UTF_8);
        Path b = Store.backupBroken(f);
        assertNotNull(b);
        assertTrue(b.getFileName().toString().startsWith("markt.json.broken-"));
        assertEquals("{ \"stalls\": { kaputt", Files.readString(b, StandardCharsets.UTF_8));
        // Original bleibt liegen (der Admin sieht es weiter unter dem gewohnten Namen)
        assertTrue(Files.exists(f));
    }

    @Test
    void ohneDateiKeineKopie(@TempDir Path dir) {
        assertNull(Store.backupBroken(dir.resolve("fehlt.json")));
    }

    /** seit 0.5.2: NaN/unendlich im spread darf weder beim Laden bleiben noch das Speichern (Server-Tick) abbrechen. */
    @Test
    void nanImLadenWirdBeimLadenErsetztUndSpeichernWirftNicht(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("markt.json"), "{\"laeden\": {\"x\": {\"id\": \"x\", \"spread\": NaN}, \"y\": {\"id\": \"y\", \"spread\": 1.0}}}",
                StandardCharsets.UTF_8);
        de.twogg.markt.core.MarktData d = Store.loadData(dir);
        assertEquals(0.6, d.laeden.get("x").spread, 0);
        assertEquals(de.twogg.markt.core.StockMath.MAX_SPREAD, d.laeden.get("y").spread, 0);
        Store.saveData(dir, d);
        assertTrue(Files.readString(dir.resolve("markt.json"), StandardCharsets.UTF_8).contains("\"spread\": 0.6"));
        // ein NaN, das trotzdem hineingerät, lässt saveData nicht mehr mit einer Ausnahme herausfallen
        d.laeden.get("x").spread = Double.NaN;
        Store.saveData(dir, d);
    }
}
