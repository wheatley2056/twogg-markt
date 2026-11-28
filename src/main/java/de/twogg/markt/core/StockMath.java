package de.twogg.markt.core;

/**
 * Preise nach Lagerbestand (seit 0.5.0). Der Preisfaktor ist 1 beim Sollbestand, verdoppelt sich bis zum leeren Lager
 * und halbiert sich bis zum vollen Lager: {@code faktor = 2^((soll − bestand) / lagerplatz · 2)} (bei Soll = halbes
 * Lager genau 2 … 0,5). Jede Einheit wird einzeln gerechnet, der Preis wandert also beim Handeln mit.
 * <p>
 * Seit 0.5.2 gilt für jede Einheit der Preis zwischen den beiden Bestandsständen, die sie verbindet: Brandt verkauft
 * zum Bestand <em>nach</em> der Entnahme und kauft zum Bestand <em>nach</em> dem Einlagern an – derselbe Schritt kostet
 * beim Kauf also immer mindestens so viel, wie er beim Ankauf bringt. Ankauf = Preis × {@code spread}, höchstens
 * {@link #MAX_SPREAD}. Gerundet wird auf Zehntel immer zugunsten des Ladens (Verkauf auf, Ankauf ab). Damit bringt keine
 * Folge von Käufen und Verkäufen, die zum selben Bestand zurückführt, Gewinn.
 */
public final class StockMath {
    private StockMath() {}

    /** Ankauf höchstens 95 % des Verkaufspreises beim gleichen Bestand (seit 0.5.2). */
    public static final double MAX_SPREAD = 0.95;
    /** Gleitkomma-Rauschen beim Aufrunden ignorieren (6,0000000001 bleibt 6). */
    private static final double EPS = 1e-9;

    public static double factor(long stock, long target, long capacity) {
        if (capacity <= 0) return 1;
        double d = (double) (Math.max(0, target) - Math.max(0, stock)) / capacity * 2.0;
        return Math.pow(2, Math.max(-3, Math.min(3, d)));
    }

    /** {@code spread} auf 0 … {@link #MAX_SPREAD} begrenzt; NaN/unendlich = 0 (seit 0.5.2). */
    public static double spread(double spread) {
        return Double.isFinite(spread) ? Math.max(0, Math.min(MAX_SPREAD, spread)) : 0;
    }

    /** Verkaufspreis (Brandt verkauft) einer Einheit beim Bestand {@code stock} in Zehnteln, aufgerundet, mindestens 0,1. */
    public static long sellPrice(long baseTenths, long stock, long target, long capacity) {
        return Math.max(1, (long) Math.ceil(baseTenths * factor(stock, target, capacity) - EPS));
    }

    /** Ankaufspreis einer Einheit beim Bestand {@code stock} in Zehnteln (abgerundet, kann 0 sein). */
    public static long buyPrice(long baseTenths, long stock, long target, long capacity, double spread) {
        return Math.max(0, (long) Math.floor(baseTenths * factor(stock, target, capacity) * spread(spread)));
    }

    /** Preis der nächsten Einheit, die Brandt verkauft (Bestand nach der Entnahme, seit 0.5.2). */
    public static long nextSellPrice(long baseTenths, long stock, long target, long capacity, int unit) {
        return sellPrice(baseTenths, Math.max(0, stock - Math.max(1, unit)), target, capacity);
    }

    /** Preis der nächsten Einheit, die Brandt ankauft (Bestand nach dem Einlagern, seit 0.5.2). */
    public static long nextBuyPrice(long baseTenths, long stock, long target, long capacity, double spread, int unit) {
        return buyPrice(baseTenths, stock + Math.max(1, unit), target, capacity, spread);
    }

    /**
     * Summe für {@code units} Einheiten, die Brandt verkauft (Bestand sinkt je Einheit um {@code unit}); jede Einheit
     * zum Bestand nach ihrer Entnahme.
     */
    public static long sellTotal(long baseTenths, long stock, long target, long capacity, int unit, int units) {
        long sum = 0;
        for (int i = 1; i <= units; i++) sum += sellPrice(baseTenths, stock - (long) i * unit, target, capacity);
        return sum;
    }

    /**
     * Summe für {@code units} Einheiten, die Brandt ankauft (Bestand steigt je Einheit um {@code unit}); jede Einheit
     * zum Bestand nach dem Einlagern.
     */
    public static long buyTotal(long baseTenths, long stock, long target, long capacity, double spread, int unit, int units) {
        long sum = 0;
        for (int i = 1; i <= units; i++) sum += buyPrice(baseTenths, stock + (long) i * unit, target, capacity, spread);
        return sum;
    }

    /** So viele Einheiten kann Brandt verkaufen: höchstens {@code wanted}, höchstens der Bestand (seit 0.5.2). */
    public static int sellable(long stock, int unit, int wanted) {
        return (int) Math.max(0, Math.min(wanted, Math.max(0, stock) / Math.max(1, unit)));
    }

    /**
     * So viele Einheiten kauft Brandt an: höchstens {@code wanted}, nur was ins Lager passt und nur, solange jede
     * Einheit noch etwas einbringt (seit 0.5.2).
     */
    public static int buyable(long baseTenths, long stock, long target, long capacity, double spread, int unit, int wanted) {
        int u = Math.max(1, unit);
        int n = (int) Math.max(0, Math.min(wanted, (capacity - stock) / u));
        int paid = 0;
        while (paid < n && buyPrice(baseTenths, stock + (long) (paid + 1) * u, target, capacity, spread) > 0) paid++;
        return paid;
    }

    /**
     * Billigster Preis einer Einheit, den Brandt je verlangt: bei vollem Lager (Bestand fällt, Preis steigt – je voller,
     * desto billiger). Für den Abgleich mit der Ankaufstelle (seit 0.6.0).
     */
    public static long cheapestSell(long baseTenths, long target, long capacity, int unit) {
        return nextSellPrice(baseTenths, capacity, target, capacity, unit);
    }
}
