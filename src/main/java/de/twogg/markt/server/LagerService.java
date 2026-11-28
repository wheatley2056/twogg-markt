package de.twogg.markt.server;

import com.google.gson.JsonElement;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.Laden;
import de.twogg.markt.core.LagerCheck;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Restock;
import de.twogg.markt.core.StockMath;
import de.twogg.markt.data.Stacks;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Brandts Lager (seit 0.6.0): gleicht den Laden {@code lager.laden} mit der Warenliste {@code lager.items} der Config ab –
 * beim Start und bei {@code /markt reload}. Bestände, Verkaufs- und Ankaufszähler bleiben erhalten; neue Waren starten
 * beim Wochenbestand. Fehlen Items (Mod nicht installiert), werden sie übersprungen und gemeldet.
 */
public final class LagerService {
    private LagerService() {}

    /** Ergebnis des letzten Abgleichs (für {@code /markt lager}). */
    private static List<String> report = List.of();

    public static List<String> report() {
        return report;
    }

    public static boolean isLager(Laden l) {
        MarktConfig.Lager c = MarktRuntime.config().lager;
        return l != null && c != null && c.enabled && c.laden.equals(l.id);
    }

    /** Abgleich ausführen. @return Meldungen (Zusammenfassung zuerst, dann übersprungene Items und Warnungen) */
    public static List<String> sync() {
        MarktConfig cfg = MarktRuntime.config();
        MarktConfig.Lager c = cfg.lager;
        List<String> out = new ArrayList<>();
        if (c == null || !c.enabled || MarktRuntime.server() == null) {
            report = List.of("Brandts Lager ist abgeschaltet (lager.enabled = false) – der Laden bleibt, wie er ist.");
            return report;
        }
        var reg = MarktRuntime.server().registryAccess();
        MarktData d = MarktRuntime.data();
        Laden l = d.laeden.get(c.laden);
        boolean neu = l == null;
        if (neu) {
            l = new Laden();
            l.id = c.laden;
            l.name = c.laden;
            d.laeden.put(l.id, l);
        }
        if (!c.name.isBlank()) l.name = c.name;
        l.subtitle = c.subtitle;
        l.spread = StockMath.spread(c.spread);
        l.deliveryDay = c.restockDay;
        l.deliveryTime = c.restockTime;
        l.zone = c.zone;
        l.trimSurplus = c.trimSurplus;
        Set<Laden.Ware> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        int added = 0;
        List<String> skipped = new ArrayList<>();
        for (MarktConfig.LagerItem it : c.items) {
            ItemStack proto = proto(reg, it);
            if (proto.isEmpty()) {
                skipped.add(it.item + (it.stack == null ? "" : " (Stapel)"));
                continue;
            }
            JsonElement json = Stacks.toJson(reg, proto.copyWithCount(1));
            Laden.Ware w = null;
            for (Laden.Ware x : l.waren) if (json.equals(x.item)) w = x;
            if (w != null && seen.contains(w)) continue;   // doppelt in der Liste: erste zählt
            long cap = it.capacity(c.capacityFactor);
            long target = Math.min(cap, it.weekly);
            if (w == null) {
                w = new Laden.Ware();
                w.id = "w" + (l.nextOffer++);
                w.item = json;
                w.stock = target;
                l.waren.add(w);
                added++;
            }
            w.managed = true;
            w.name = it.name.isBlank() ? proto.getHoverName().getString() : it.name;
            w.unit = Math.max(1, it.unit);
            w.base = it.baseTenths(c.capacityFactor);
            w.capacity = cap;
            w.target = target;
            w.stock = Math.max(0, Math.min(w.stock, w.capacity));
            seen.add(w);
        }
        // Waren aus der Config, die dort nicht mehr stehen, gehen weg; Waren ohne lesbares Item (Mod fehlt) bleiben liegen
        int removed = 0;
        for (Laden.Ware w : List.copyOf(l.waren)) {
            if (!w.managed || seen.contains(w) || Stacks.fromJson(reg, w.item).isEmpty()) continue;
            l.waren.remove(w);
            removed++;
        }
        long now = MarktRuntime.now();
        // Kein Sofort-Nachschub beim ersten Abgleich: neue Waren stehen schon beim Wochenbestand
        if (neu || l.lastDelivery <= 0) l.lastDelivery = Restock.lastSlot(now, day(l), time(l), Restock.zone(l.zone));
        MarktRuntime.markDirty();
        out.add(l.waren.size() + " Waren in „" + l.name + "“ (" + l.id + ")" + (added > 0 ? ", " + added + " neu" : "")
                + (removed > 0 ? ", " + removed + " entfernt" : "") + (skipped.isEmpty() ? "" : ", " + skipped.size() + " übersprungen")
                + " · Nachschub " + when(l) + ", nächster " + nextText(l, now) + ".");
        for (String s : skipped) out.add("unbekanntes Item (Mod fehlt oder ID falsch), übersprungen: " + s);
        if (!l.sells.isEmpty()) out.add("Achtung: " + l.sells.size() + " Verkaufszeilen ohne Bestand (unbegrenzt) – entfernen mit /markt laden entfernen " + l.id + " <zeile>.");
        for (String s : LagerCheck.buyerConflicts(cfg)) out.add("Warnung Geldpumpe: " + s);
        report = List.copyOf(out);
        return report;
    }

    static ItemStack proto(net.minecraft.core.HolderLookup.Provider reg, MarktConfig.LagerItem it) {
        if (it.stack != null) return Stacks.fromJson(reg, it.stack);
        Item item = Stacks.item(it.item.contains(":") ? it.item : "minecraft:" + it.item);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    static java.time.DayOfWeek day(Laden l) {
        return Restock.day(l.deliveryDay, java.time.DayOfWeek.MONDAY);
    }

    static java.time.LocalTime time(Laden l) {
        return Restock.time(l.deliveryTime, java.time.LocalTime.of(6, 0));
    }

    /** „jeden Sonntag 06:00“. */
    public static String when(Laden l) {
        return "jeden " + day(l).getDisplayName(TextStyle.FULL, Locale.GERMAN) + " " + time(l);
    }

    /** Nächster Nachschub als Datum und Uhrzeit in der Zeitzone des Ladens. */
    public static String nextText(Laden l, long now) {
        var z = Restock.zone(l.zone);
        long next = Restock.nextSlot(now, day(l), time(l), z);
        return java.time.format.DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMAN).format(java.time.Instant.ofEpochMilli(next).atZone(z))
                + " (in " + MarketMath.duration(next - now) + ")";
    }
}
