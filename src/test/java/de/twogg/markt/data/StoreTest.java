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
}
