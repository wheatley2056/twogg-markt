package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Brandts Lager (seit 0.6.0): Warenliste, Preise, Wochenbestand für 42 Spieler, Ankauf mit Obergrenze, keine Geldpumpe. */
class LagerTest {
    private static final MarktConfig.Lager LAGER = MarktConfig.defaultLager();

    private static MarktConfig.LagerItem get(String id) {
        for (MarktConfig.LagerItem i : LAGER.items) if (i.item.equals(id) && i.stack == null) return i;
        for (MarktConfig.LagerItem i : LAGER.items) if (i.stack != null && i.stack.toString().contains(id)) return i;
        fail("fehlt: " + id);
        return null;
    }

    /** Coins je Stück beim Wochenbestand. */
    private static double perPiece(String id) {
        MarktConfig.LagerItem i = get(id);
        return i.price / i.unit;
    }

    @Test
    void containsExactlyTheRequestedList() {
        assertEquals(107, LAGER.items.size());
        Set<String> keys = new HashSet<>();
        for (MarktConfig.LagerItem i : LAGER.items) assertTrue(keys.add(i.item + i.stack), "doppelt: " + i);
        for (String c : new String[]{"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime", "green", "cyan",
                "light_blue", "blue", "purple", "magenta", "pink"}) get("minecraft:" + c + "_dye");
        for (String t : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"}) {
            get("minecraft:" + t + "_log");
            get("minecraft:" + t + "_leaves");
        }
        for (String m : new String[]{"autumnal_winterly:fir_log", "autumnal_winterly:fir_leaves", "arts_and_crafts:cork_log",
                "arts_and_crafts:cork_leaves", "autumnal_winterly:raw_platinum", "autumnal_winterly:kaolin", "arts_and_crafts:gypsum",
                "arts_and_crafts:soapstone", "autumnal_winterly:winterberry_seeds", "minecraft:dried_ghast", "minecraft:dragon_breath",
                "minecraft:piglin_banner_pattern", "minecraft:lava_bucket"}) get(m);
        // Verzauberungsbücher mit Datenkomponente
        for (String e : new String[]{"\"minecraft:mending\":1", "\"minecraft:unbreaking\":3", "\"minecraft:efficiency\":4", "\"minecraft:swift_sneak\":3"}) {
            MarktConfig.LagerItem b = get(e);
            assertEquals("minecraft:enchanted_book", b.stack.getAsJsonObject().get("id").getAsString());
            assertNotNull(b.stack.getAsJsonObject().getAsJsonObject("components").get("minecraft:stored_enchantments"));
        }
    }

    @Test
    void rareThingsCostClearlyMore() {
        double mending = get("\"minecraft:mending\":1").price;
        assertTrue(mending >= 20, "Reparatur ist das Seltenste: " + mending);
        assertTrue(get("\"minecraft:swift_sneak\":3").price > get("\"minecraft:unbreaking\":3").price);
        for (String r : new String[]{"minecraft:music_disc_pigstep", "minecraft:music_disc_tears", "minecraft:rib_armor_trim_smithing_template",
                "minecraft:snout_armor_trim_smithing_template", "minecraft:spire_armor_trim_smithing_template"}) {
            assertTrue(perPiece(r) >= 10, r);
            assertTrue(perPiece(r) > perPiece("minecraft:diamond") * 2, r + " deutlich über Diamant");
        }
        assertTrue(perPiece("minecraft:dragon_breath") > perPiece("minecraft:ghast_tear"));
        assertTrue(perPiece("minecraft:ghast_tear") > perPiece("minecraft:blaze_rod"));
        assertTrue(perPiece("minecraft:diamond") > perPiece("minecraft:raw_gold"));
        assertTrue(perPiece("minecraft:raw_gold") > perPiece("minecraft:raw_iron"));
        assertTrue(perPiece("minecraft:raw_iron") > perPiece("minecraft:raw_copper"));
        assertTrue(perPiece("minecraft:end_stone") > perPiece("minecraft:cobblestone"));
        assertTrue(perPiece("minecraft:cobblestone") > perPiece("minecraft:dirt"));
        // Massenware: ein Stack kostet höchstens eine Arbeitsstunde (1,5 WLC Stundenlohn)
        for (MarktConfig.LagerItem i : LAGER.items) if (i.unit == 64) assertTrue(i.price <= 1.5 * 2, i + ": " + i.price);
    }

    /** 42 Spieler: Von allem, was weniger als 2 WLC je Einheit kostet, gibt es pro Woche mindestens eine Einheit je Spieler. */
    @Test
    void weeklyStockIsSizedFor42Players() {
        for (MarktConfig.LagerItem i : LAGER.items) {
            assertEquals(0, i.weekly % i.unit, i + ": Wochenbestand in ganzen Einheiten");
            long units = i.weekly / i.unit;
            if (i.price < 2.0) assertTrue(units >= 42, i + ": nur " + units + " Einheiten");
            else assertTrue(units >= 2, i + ": " + units);
        }
        assertTrue(get("minecraft:cobblestone").weekly >= 42 * 64 * 4, "Bruchstein: 4 Stacks je Spieler");
    }

    /** Im Fenster steht beim Wochenbestand genau der Preis aus der Config (bzw. knapp darunter, wenn er sich nicht treffen lässt). */
    @Test
    void shownPriceAtWeeklyStockIsTheConfiguredPrice() {
        for (MarktConfig.LagerItem i : LAGER.items) {
            long cap = i.capacity(LAGER.capacityFactor), b = i.baseTenths(LAGER.capacityFactor);
            long shown = StockMath.nextSellPrice(b, i.weekly, i.weekly, cap, i.unit), want = MarketMath.tenths(i.price);
            assertTrue(shown <= want && shown >= want - 1, i + ": " + shown + " statt " + want);
            assertTrue(StockMath.nextSellPrice(b + 1, i.weekly, i.weekly, cap, i.unit) > want, i + ": größter passender Grundpreis");
        }
        MarktConfig.LagerItem cobble = get("minecraft:cobblestone");
        assertEquals(4, StockMath.nextSellPrice(cobble.baseTenths(2), cobble.weekly, cobble.weekly, cobble.capacity(2), 64), "Bruchstein 0,4");
    }

    @Test
    void capacityIsDoubleTheWeeklyStockInWholeUnits() {
        for (MarktConfig.LagerItem i : LAGER.items) {
            long cap = i.capacity(LAGER.capacityFactor);
            assertEquals(2 * i.weekly, cap, i.toString());
            assertEquals(0, cap % i.unit);
        }
        MarktConfig.LagerItem x = new MarktConfig.LagerItem("minecraft:coal", "", 32, 1, 100);
        assertEquals(224, x.capacity(2.0), "200 → auf ganze 32er aufgerundet");
        x.capacity = 50;
        assertEquals(160, x.capacity(2.0), "eigener Lagerplatz, aber mindestens Soll + eine Einheit");
        x.weekly = 0;
        assertEquals(64, x.capacity(2.0));
    }

    /** Verkaufen füllt das Lager nur bis zum Lagerplatz, jede weitere Einheit bringt weniger; dann ist Schluss. */
    @Test
    void sellingToBrandtStopsAtTheCap() {
        MarktConfig.LagerItem m = get("\"minecraft:mending\":1");
        long base = m.baseTenths(LAGER.capacityFactor), cap = m.capacity(LAGER.capacityFactor), target = m.weekly;
        int n = StockMath.buyable(base, target, target, cap, LAGER.spread, 1, 100);
        assertEquals(cap - target, n, "nur bis Lagerplatz " + cap);
        long last = Long.MAX_VALUE;
        for (int k = 1; k <= n; k++) {
            long p = StockMath.buyPrice(base, target + k, target, cap, LAGER.spread);
            assertTrue(p < last && p > 0, "fällt: " + p);
            last = p;
        }
        assertEquals(0, StockMath.buyable(base, cap, target, cap, LAGER.spread, 1, 100), "voll → nimmt nichts mehr");
        // Rückkauf immer unter dem Verkaufspreis
        for (MarktConfig.LagerItem i : LAGER.items) {
            long b = i.baseTenths(LAGER.capacityFactor), c = i.capacity(LAGER.capacityFactor);
            assertTrue(StockMath.nextBuyPrice(b, i.weekly, i.weekly, c, LAGER.spread, i.unit) < StockMath.nextSellPrice(b, i.weekly, i.weekly, c, i.unit), i.toString());
        }
    }

    /**
     * Geldpumpe (0.5.2) bleibt zu: Für jede Ware der Vorlage, jeden Bestand in ganzen Einheiten (dazu zufällige
     * krumme Bestände) und jede Menge 1 … 64 bringt weder „kaufen, dann verkaufen“ noch „verkaufen, dann kaufen“ Gewinn.
     */
    @Test
    void noRoundTripPaysForTheDefaultLager() {
        Random rnd = new Random(7);
        int checked = 0;
        for (MarktConfig.LagerItem i : LAGER.items) {
            long base = i.baseTenths(LAGER.capacityFactor), cap = i.capacity(LAGER.capacityFactor), target = i.weekly;
            int u = i.unit;
            for (double sp : new double[]{LAGER.spread, StockMath.MAX_SPREAD}) {
                for (long s0 = 0; s0 <= cap + u; s0 += u) {
                    long s = Math.min(cap, s0 > cap ? rnd.nextLong(cap + 1) : s0);
                    for (int n = 1; n <= 64; n += (cap / u > 400 ? 7 : 1)) {
                        int k = StockMath.sellable(s, u, n);
                        long cost = StockMath.sellTotal(base, s, target, cap, u, k);
                        long s2 = s - (long) k * u;
                        int m = StockMath.buyable(base, s2, target, cap, sp, u, k);
                        long back = StockMath.buyTotal(base, s2, target, cap, sp, u, m);
                        if (k > 0 && back >= cost) fail(i + ": kaufen " + k + " bei " + s + " kostet " + cost + ", zurück " + back);
                        m = StockMath.buyable(base, s, target, cap, sp, u, n);
                        long got = StockMath.buyTotal(base, s, target, cap, sp, u, m);
                        long s3 = s + (long) m * u;
                        cost = StockMath.sellTotal(base, s3, target, cap, u, StockMath.sellable(s3, u, m));
                        if (m > 0 && got >= cost) fail(i + ": verkaufen " + m + " bei " + s + " bringt " + got + ", zurück " + cost);
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 100_000, "geprüft: " + checked);
    }

    /** Ankaufstelle der Vorlage zahlt je Stück nie mehr, als Brandt mindestens verlangt (auch Roherz → Barren). */
    @Test
    void buyerDefaultsNeverUndercutBrandt() {
        MarktConfig c = MarktConfig.defaults();
        assertEquals(List.of(), LagerCheck.buyerConflicts(c));
        // die Prüfung schlägt an, wenn jemand den Ankauf hochsetzt
        c.buyerItem("minecraft:iron_ingot").price = 1.0;
        List<String> w = LagerCheck.buyerConflicts(c);
        assertEquals(1, w.size(), w.toString());
        assertTrue(w.getFirst().startsWith("minecraft:raw_iron"), w.getFirst());
        // Kerzen (Faden + Honigwabe) bringen an der Ankaufstelle nicht mehr, als die Zutaten bei Brandt mindestens kosten
        MarktConfig.LagerItem s = get("minecraft:string"), h = get("minecraft:honeycomb");
        double candle = cheapest(s) + cheapest(h);
        MarktConfig.BuyerItem bi = c.buyerItem("minecraft:candle");
        assertTrue(MarketMath.tenths(bi.price) / (double) bi.bundle <= candle, "Kerze " + candle);
    }

    private static double cheapest(MarktConfig.LagerItem i) {
        return StockMath.cheapestSell(i.baseTenths(LAGER.capacityFactor), i.weekly, i.capacity(LAGER.capacityFactor), i.unit) / (double) i.unit;
    }

    @Test
    void sanitizeRepairsLager() {
        MarktConfig c = new com.google.gson.Gson().fromJson("""
                {"configVersion": 2, "lager": {"laden": "Brandt Lager!", "restockDay": "Sonntag", "restockTime": "xx", "zone": "Mond",
                 "spread": 3, "capacityFactor": 0,
                 "items": [null, {"item": ""}, {"item": "minecraft:coal", "unit": 0, "price": -1, "weekly": -5}]}}""", MarktConfig.class);
        c.sanitize();
        assertEquals("materiallager", c.lager.laden);
        assertEquals("SUNDAY", c.lager.restockDay);
        assertEquals("06:00", c.lager.restockTime);
        assertEquals("Europe/Berlin", c.lager.zone);
        assertEquals(StockMath.MAX_SPREAD, c.lager.spread);
        assertEquals(1, c.lager.capacityFactor);
        assertEquals(1, c.lager.items.size());
        MarktConfig.LagerItem i = c.lager.items.getFirst();
        assertEquals(1, i.unit);
        assertEquals(0.1, i.price);
        assertEquals(0, i.weekly);
        // Gson übernimmt Stapel-JSON und schreibt ihn wieder
        String json = new com.google.gson.Gson().toJson(MarktConfig.defaults());
        MarktConfig back = new com.google.gson.Gson().fromJson(json, MarktConfig.class);
        back.sanitize();
        assertEquals(107, back.lager.items.size());
        Map<String, Integer> books = new HashMap<>();
        for (MarktConfig.LagerItem x : back.lager.items) if (x.stack != null) books.merge(x.item, 1, Integer::sum);
        assertEquals(Map.of("minecraft:enchanted_book", 4), books);
    }
}
