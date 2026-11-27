package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Preise nach Lagerbestand (seit 0.5.0). */
class StockMathTest {

    @Test
    void factorIsOneAtTargetTwoWhenEmptyHalfWhenFull() {
        assertEquals(1.0, StockMath.factor(1024, 1024, 2048), 1e-9);
        assertEquals(2.0, StockMath.factor(0, 1024, 2048), 1e-9);
        assertEquals(0.5, StockMath.factor(2048, 1024, 2048), 1e-9);
    }

    @Test
    void buyingBackNeverPays() {
        long sell = StockMath.sellPrice(10, 500, 1024, 2048);
        long back = StockMath.buyPrice(10, 500, 1024, 2048, 0.6);
        assertTrue(back < sell, back + " < " + sell);
    }

    @Test
    void dumpingAHundredStacksDoesNotMakeYouRich() {
        // Bruchstein: 1 Einheit = 64, Grundpreis 0,5 WLC, Lager 32 Stacks, Soll 16 Stacks
        int unit = 64;
        long cap = 32 * 64, target = 16 * 64;
        int space = StockMath.buyable(5, target, target, cap, 0.6, unit, 100); // nur 16 Stacks passen noch hinein
        long earned = StockMath.buyTotal(5, target, target, cap, 0.6, unit, space);
        assertEquals(16, space);
        assertTrue(earned <= 16 * 3, "höchstens 0,3 je Stack, fallend: " + earned);
        assertTrue(earned >= 16, "aber nicht nichts: " + earned);
    }

    @Test
    void priceMovesWhileTrading() {
        long first = StockMath.sellPrice(100, 1024, 1024, 2048);
        long tenth = StockMath.sellPrice(100, 1024 - 9 * 64, 1024, 2048);
        assertTrue(tenth > first, "wird teurer, je leerer: " + first + " → " + tenth);
        // seit 0.5.2: jede Einheit zum Bestand nach ihrer Entnahme
        assertEquals(StockMath.sellPrice(100, 1024 - 64, 1024, 2048) + StockMath.sellPrice(100, 1024 - 128, 1024, 2048),
                StockMath.sellTotal(100, 1024, 1024, 2048, 64, 2));
        assertEquals(StockMath.buyPrice(100, 1024 + 64, 1024, 2048, 0.6) + StockMath.buyPrice(100, 1024 + 128, 1024, 2048, 0.6),
                StockMath.buyTotal(100, 1024, 1024, 2048, 0.6, 64, 2));
    }

    // ------------------------------------------------------------------ Geldpumpe (seit 0.5.2)

    /** Der Fall aus der Prüfung: Schallplatte (Einheit 1, Lager 2, Soll 1, 6,0 WLC), früher +1,2 je Runde. */
    @Test
    void pigstepIsNoLongerAMoneyPump() {
        long base = 60, cap = 2, target = 1;
        long paid = StockMath.sellTotal(base, 1, target, cap, 1, 1);          // Bestand 1 → 0
        long back = StockMath.buyTotal(base, 0, target, cap, 0.6, 1, 1);      // Bestand 0 → 1
        assertTrue(back < paid, "Rückverkauf " + back + " < Kauf " + paid);
        assertEquals(StockMath.nextSellPrice(base, 1, target, cap, 1), paid, "Anzeige = abgerechneter Preis");
        assertEquals(StockMath.nextBuyPrice(base, 0, target, cap, 0.6, 1), back, "Anzeige = abgerechneter Preis");
    }

    @Test
    void spreadIsCapped() {
        assertEquals(StockMath.MAX_SPREAD, StockMath.spread(1.0), 0);
        assertEquals(StockMath.MAX_SPREAD, StockMath.spread(5.0), 0);
        assertEquals(0.6, StockMath.spread(0.6), 0);
        assertEquals(0, StockMath.spread(-1), 0);
        assertEquals(0, StockMath.spread(Double.NaN), 0);
        assertEquals(0, StockMath.spread(Double.POSITIVE_INFINITY), 0);
        assertEquals(0, StockMath.buyPrice(60, 0, 1, 2, Double.NaN));
        // spread 1 wirkt wie 0,95: Ankauf beim gleichen Bestand nie gleich dem Verkaufspreis
        for (long s = 0; s <= 2048; s++) {
            assertTrue(StockMath.buyPrice(100, s, 1024, 2048, 1.0) < StockMath.sellPrice(100, s, 1024, 2048), "Bestand " + s);
        }
    }

    /** Rundung auf Zehntel immer zugunsten des Ladens: Verkauf nie unter, Ankauf nie über dem genauen Wert. */
    @Test
    void roundingAlwaysFavoursTheShop() {
        for (Ware w : shippedWares()) {
            for (long s = 0; s <= w.cap; s++) {
                double exact = w.base * StockMath.factor(s, w.target, w.cap);
                assertTrue(StockMath.sellPrice(w.base, s, w.target, w.cap) >= exact - 1e-6, w + " Bestand " + s);
                assertTrue(StockMath.buyPrice(w.base, s, w.target, w.cap, w.spread) <= exact * w.spread + 1e-6, w + " Bestand " + s);
            }
        }
    }

    /**
     * Pflicht: Für jede mitgelieferte Ware, jeden Bestand 0 … Lager und jede Menge 1 … 64 bringt weder „kaufen, dann
     * verkaufen“ noch „verkaufen, dann kaufen“ Gewinn – gerechnet mit denselben Grenzen wie der Laden (nur, was da ist,
     * nur, was ins Lager passt und etwas einbringt). Geprüft mit dem spread der Datei und mit dem Höchstwert.
     */
    @Test
    void noRoundTripEverPaysForShippedStores() {
        int checked = 0;
        for (Ware w : shippedWares()) {
            for (double sp : new double[]{w.spread, StockMath.MAX_SPREAD, 1.0}) {
                for (long s = 0; s <= w.cap; s++) {
                    for (int n = 1; n <= 64; n++) {
                        // kaufen, dann zurückverkaufen
                        int k = StockMath.sellable(s, w.unit, n);
                        long cost = StockMath.sellTotal(w.base, s, w.target, w.cap, w.unit, k);
                        long s2 = s - (long) k * w.unit;
                        int m = StockMath.buyable(w.base, s2, w.target, w.cap, sp, w.unit, k);
                        long back = StockMath.buyTotal(w.base, s2, w.target, w.cap, sp, w.unit, m);
                        if (back - cost > 0 || (k > 0 && back >= cost)) {
                            fail(w + " spread " + sp + ": kaufen " + k + " bei Bestand " + s + " kostet " + cost + ", zurück " + back);
                        }
                        // verkaufen, dann zurückkaufen
                        m = StockMath.buyable(w.base, s, w.target, w.cap, sp, w.unit, n);
                        long got = StockMath.buyTotal(w.base, s, w.target, w.cap, sp, w.unit, m);
                        s2 = s + (long) m * w.unit;
                        k = StockMath.sellable(s2, w.unit, m);
                        assertEquals(m, k);
                        cost = StockMath.sellTotal(w.base, s2, w.target, w.cap, w.unit, k);
                        if (got - cost > 0 || (m > 0 && got >= cost)) {
                            fail(w + " spread " + sp + ": verkaufen " + m + " bei Bestand " + s + " bringt " + got + ", zurück " + cost);
                        }
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 1_000_000, "geprüft: " + checked);
    }

    /** Beliebige Folgen von Käufen und Verkäufen, die zum Ausgangsbestand zurückführen, bringen nie Gewinn. */
    @Test
    void randomTradeSequencesNeverPay() {
        Random rnd = new Random(42);
        for (Ware w : shippedWares()) {
            for (int run = 0; run < 300; run++) {
                long start = rnd.nextInt((int) w.cap + 1), s = start, money = 0;
                int ops = 1 + rnd.nextInt(30);
                for (int i = 0; i < ops; i++) {
                    int n = 1 + rnd.nextInt(64);
                    if (rnd.nextBoolean()) {
                        int k = StockMath.sellable(s, w.unit, n);
                        money -= StockMath.sellTotal(w.base, s, w.target, w.cap, w.unit, k);
                        s -= (long) k * w.unit;
                    } else {
                        int m = StockMath.buyable(w.base, s, w.target, w.cap, w.spread, w.unit, n);
                        money += StockMath.buyTotal(w.base, s, w.target, w.cap, w.spread, w.unit, m);
                        s += (long) m * w.unit;
                    }
                }
                // zurück zum Ausgangsbestand (so weit der Laden mitmacht)
                while (s < start) {
                    int k = (int) ((start - s) / w.unit);
                    int m = StockMath.buyable(w.base, s, w.target, w.cap, w.spread, w.unit, Math.min(64, k));
                    if (m == 0) break;
                    money += StockMath.buyTotal(w.base, s, w.target, w.cap, w.spread, w.unit, m);
                    s += (long) m * w.unit;
                }
                while (s > start) {
                    int k = StockMath.sellable(s, w.unit, (int) Math.min(64, (s - start) / w.unit));
                    money -= StockMath.sellTotal(w.base, s, w.target, w.cap, w.unit, k);
                    s -= (long) k * w.unit;
                }
                // s ≤ start: der Spieler hat höchstens so viel Ware wie vorher (Rest nimmt der Laden nicht mehr an)
                assertFalse(s > start);
                assertTrue(money <= 0, w + ": Folge von " + ops + " Geschäften ab Bestand " + start + " bringt " + money);
            }
        }
    }

    /**
     * W4 (seit 0.5.2): Die Ankaufstelle der Vorlage zahlt je Stück nie mehr, als das mitgelieferte Materiallager für
     * dasselbe Item mindestens verlangt – sonst lohnt „bei Brandt kaufen, an der Ankaufstelle abgeben“.
     */
    @Test
    void buyerDefaultsNeverPayMoreThanTheShippedStoreCharges() throws Exception {
        JsonObject root = JsonParser.parseString(Files.readString(Path.of("beispiele/materiallager.json"))).getAsJsonObject();
        int compared = 0;
        for (MarktConfig.BuyerItem bi : MarktConfig.defaults().buyer.items) {
            for (var el : root.getAsJsonArray("waren")) {
                JsonObject o = el.getAsJsonObject();
                if (!o.has("item")) continue;
                String id = o.get("item").getAsString();
                if (!(id.contains(":") ? id : "minecraft:" + id).equals(bi.item)) continue;
                int unit = o.get("einheit").getAsInt();
                long base = MarketMath.tenths(o.get("preis").getAsDouble()), cap = o.get("lager").getAsLong(), target = o.get("soll").getAsLong();
                double cheapest = Double.MAX_VALUE;
                for (long st = unit; st <= cap; st++) cheapest = Math.min(cheapest, StockMath.nextSellPrice(base, st, target, cap, unit) / (double) unit);
                double pays = MarketMath.tenths(bi.price) / (double) bi.bundle;
                assertTrue(pays <= cheapest + 1e-9, bi.item + ": Ankaufstelle " + pays + " > Materiallager " + cheapest + " Zehntel je Stück");
                compared++;
            }
        }
        assertTrue(compared >= 3, "Kohle, Fichtenholz, Wolle verglichen: " + compared);
    }

    // ------------------------------------------------------------------ mitgelieferte Lager

    record Ware(String name, long base, int unit, long cap, long target, double spread) {}

    /** Alle Waren aus den mitgelieferten Lager-Dateien, gleiche Zahlen nur einmal (wie der Import sie übernimmt). */
    static List<Ware> shippedWares() {
        List<Ware> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (var files = Files.list(Path.of("beispiele"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                double spread = root.has("spread") ? root.get("spread").getAsDouble() : 0.6;
                for (var el : root.getAsJsonArray("waren")) {
                    JsonObject o = el.getAsJsonObject();
                    int unit = Math.max(1, Math.min(64 * 9, o.has("einheit") ? o.get("einheit").getAsInt() : 1));
                    long base = MarketMath.tenths(o.get("preis").getAsDouble());
                    long cap = Math.max(unit, o.has("lager") ? o.get("lager").getAsLong() : 64);
                    long target = Math.max(0, Math.min(cap, o.has("soll") ? o.get("soll").getAsLong() : 32));
                    String key = base + "/" + unit + "/" + cap + "/" + target + "/" + spread;
                    if (seen.add(key)) {
                        out.add(new Ware(f.getFileName() + ":" + (o.has("name") ? o.get("name").getAsString() : "?"), base, unit, cap, target, spread));
                    }
                }
            }
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        assertTrue(out.size() > 10, "mitgelieferte Waren gefunden: " + out.size());
        return out;
    }
}
