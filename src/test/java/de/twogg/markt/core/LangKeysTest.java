package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Jedes Item (assets/twogg_markt/items/*.json) hat einen Namen in beiden Sprachen – sonst zeigt das Spiel den Schlüssel. */
class LangKeysTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/twogg_markt");

    @Test
    void everyItemHasAName() throws IOException {
        JsonObject de = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/de_de.json"))).getAsJsonObject();
        JsonObject en = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/en_us.json"))).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.list(ASSETS.resolve("items"))) {
            for (Path f : files.toList()) {
                String id = f.getFileName().toString().replace(".json", "");
                boolean block = Files.exists(ASSETS.resolve("blockstates/" + id + ".json"));
                String key = (block ? "block" : "item") + ".twogg_markt." + id;
                if (!de.has(key)) missing.add("de_de: " + key);
                if (!en.has(key)) missing.add("en_us: " + key);
            }
        }
        assertTrue(missing.isEmpty(), "fehlende Namen: " + missing);
    }
}
