package de.twogg.markt.core;

/**
 * Preise nach Lagerbestand (seit 0.5.0). Der Preisfaktor ist 1 beim Sollbestand, verdoppelt sich bis zum leeren Lager
 * und halbiert sich bis zum vollen Lager: {@code faktor = 2^((soll − bestand) / lagerplatz · 2)} (bei Soll = halbes
 * Lager genau 2 … 0,5). Jede Einheit wird einzeln gerechnet, der Preis wandert also beim Handeln mit. Brandt kauft zum
 * Verkaufspreis × {@code spread} an – kaufen und sofort zurückverkaufen lohnt nie.
 */
public final class StockMath {
    private StockMath() {}

    public static double factor(long stock, long target, long capacity) {
        if (capacity <= 0) return 1;
        double d = (double) (Math.max(0, target) - Math.max(0, stock)) / capacity * 2.0;
        return Math.pow(2, Math.max(-3, Math.min(3, d)));
    }

    /** Verkaufspreis (Brandt verkauft) einer Einheit in Zehnteln, mindestens 0,1. */
    public static long sellPrice(long baseTenths, long stock, long target, long capacity) {
        return Math.max(1, Math.round(baseTenths * factor(stock, target, capacity)));
    }

    /** Ankaufspreis einer Einheit in Zehnteln (abgerundet, kann 0 sein). */
    public static long buyPrice(long baseTenths, long stock, long target, long capacity, double spread) {
        return (long) Math.floor(baseTenths * factor(stock, target, capacity) * Math.max(0, Math.min(1, spread)));
    }

    /** Summe für {@code units} Einheiten, die Brandt verkauft (Bestand sinkt je Einheit um {@code unit}). */
    public static long sellTotal(long baseTenths, long stock, long target, long capacity, int unit, int units) {
        long sum = 0;
        for (int i = 0; i < units; i++) sum += sellPrice(baseTenths, stock - (long) i * unit, target, capacity);
        return sum;
    }

    /** Summe für {@code units} Einheiten, die Brandt ankauft (Bestand steigt je Einheit um {@code unit}). */
    public static long buyTotal(long baseTenths, long stock, long target, long capacity, double spread, int unit, int units) {
        long sum = 0;
        for (int i = 0; i < units; i++) sum += buyPrice(baseTenths, stock + (long) i * unit, target, capacity, spread);
        return sum;
    }
}
