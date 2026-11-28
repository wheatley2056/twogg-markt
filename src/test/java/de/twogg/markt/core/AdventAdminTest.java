package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Adventskalender bearbeiten (seit 0.6.0): Türchen anlegen, Stapel mit Daten speichern, auf die Vorlage zurücksetzen. */
class AdventAdminTest {
    @Test
    void createSortedAndOnlyOneToTwentyFour() {
        MarktConfig c = new MarktConfig();
        c.adventOrCreate(5).coins = 2;
        c.adventOrCreate(2);
        assertEquals(2, c.event.adventDays.getFirst().day);
        assertEquals(2.0, c.adventOrCreate(5).coins, "vorhandenes Türchen");
        assertThrows(IllegalArgumentException.class, () -> c.adventOrCreate(25));
        assertThrows(IllegalArgumentException.class, () -> c.adventOrCreate(0));
    }

    @Test
    void stackSurvivesSaveAndLoad() {
        MarktConfig c = MarktConfig.defaults();
        MarktConfig.AdventDay d = c.adventOrCreate(13);
        d.stack = JsonParser.parseString("{\"id\":\"minecraft:diamond_sword\",\"count\":1,\"components\":{\"minecraft:custom_name\":\"Weihnachtsklinge\"}}");
        d.item = "minecraft:diamond_sword";
        d.count = 1;
        MarktConfig back = new Gson().fromJson(new Gson().toJson(c), MarktConfig.class);
        back.sanitize();
        assertNotNull(back.advent(13).stack);
        assertEquals("Weihnachtsklinge", back.advent(13).stack.getAsJsonObject().getAsJsonObject("components").get("minecraft:custom_name").getAsString());
        // JSON null wird zu „kein Stapel“
        MarktConfig n = new Gson().fromJson("{\"event\": {\"adventDays\": [{\"day\": 3, \"stack\": null, \"coins\": 1}]}}", MarktConfig.class);
        n.sanitize();
        assertNull(n.advent(3).stack);
    }

    @Test
    void resetOneOrAll() {
        MarktConfig c = MarktConfig.defaults();
        MarktConfig tpl = MarktConfig.defaults();
        c.adventOrCreate(6).coins = 99;
        c.adventOrCreate(6).text = "anders";
        c.adventOrCreate(7).coins = 42;
        c.resetAdvent(6);
        assertEquals(tpl.advent(6).coins, c.advent(6).coins);
        assertEquals("Nikolaus!", c.advent(6).text);
        assertEquals(42, c.advent(7).coins, "nur Türchen 6");
        c.resetAdvent(0);
        assertEquals(24, c.event.adventDays.size());
        assertEquals(tpl.advent(7).coins, c.advent(7).coins);
    }
}
