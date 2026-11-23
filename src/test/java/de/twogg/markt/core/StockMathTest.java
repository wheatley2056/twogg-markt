package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        long space = (cap - target) / unit; // nur 16 Stacks passen noch hinein
        long earned = StockMath.buyTotal(5, target, target, cap, 0.6, unit, (int) space);
        assertEquals(16, space);
        assertTrue(earned <= 16 * 3, "höchstens 0,3 je Stack, fallend: " + earned);
        assertTrue(earned >= 16, "aber nicht nichts: " + earned);
    }

    @Test
    void priceMovesWhileTrading() {
        long first = StockMath.sellPrice(100, 1024, 1024, 2048);
        long tenth = StockMath.sellPrice(100, 1024 - 9 * 64, 1024, 2048);
        assertTrue(tenth > first, "wird teurer, je leerer: " + first + " → " + tenth);
        assertEquals(StockMath.sellPrice(100, 1024, 1024, 2048) + StockMath.sellPrice(100, 1024 - 64, 1024, 2048),
                StockMath.sellTotal(100, 1024, 1024, 2048, 64, 2));
    }
}
