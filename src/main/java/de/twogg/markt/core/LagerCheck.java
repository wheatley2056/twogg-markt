package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prüfungen für Brandts Lager ohne Minecraft (seit 0.6.0): Die Ankaufstelle darf je Stück nie mehr zahlen, als Brandt
 * für dasselbe Item (oder das Roherz, aus dem es geschmolzen wird) mindestens verlangt – sonst bringt „bei Brandt kaufen,
 * an der Ankaufstelle abgeben“ Geld aus dem Nichts.
 */
public final class LagerCheck {
    private LagerCheck() {}

    /** Roherz → Barren (Schmelzofen): gilt für den Vergleich wie dasselbe Item. */
    static final Map<String, String> SMELTS = Map.of(
            "minecraft:raw_iron", "minecraft:iron_ingot",
            "minecraft:raw_gold", "minecraft:gold_ingot",
            "minecraft:raw_copper", "minecraft:copper_ingot",
            "autumnal_winterly:raw_platinum", "autumnal_winterly:platinum_ingot");

    /** @return eine Zeile je Ware, bei der die Ankaufstelle mehr zahlt als Brandts billigster Preis */
    public static List<String> buyerConflicts(MarktConfig c) {
        List<String> out = new ArrayList<>();
        if (c.lager == null || c.buyer == null) return out;
        for (MarktConfig.LagerItem it : c.lager.items) {
            if (it.stack != null) continue;
            long base = it.baseTenths(c.lager.capacityFactor);
            long cap = it.capacity(c.lager.capacityFactor), target = Math.min(cap, it.weekly);
            double cheapest = StockMath.cheapestSell(base, target, cap, it.unit) / (double) Math.max(1, it.unit);
            for (MarktConfig.BuyerItem bi : c.buyer.items) {
                if (!bi.item.equals(it.item) && !bi.item.equals(SMELTS.get(it.item))) continue;
                double pays = MarketMath.tenths(bi.price) / (double) Math.max(1, bi.bundle);
                if (pays > cheapest + 1e-9) {
                    out.add(String.format(java.util.Locale.GERMANY, "%s: Ankaufstelle zahlt %.3f je Stück für %s, Brandt verlangt mindestens %.3f",
                            it.item, pays / 10, bi.item, cheapest / 10));
                }
            }
        }
        return out;
    }
}
