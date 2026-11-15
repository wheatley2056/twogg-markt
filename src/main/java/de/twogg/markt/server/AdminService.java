package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.Job;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Plot;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;

/** Marktverwaltung (Fenster „admin“, Toolbox-Kachel, {@code /markt admin}). Jede Aktion prüft die Rechte neu. */
public final class AdminService {
    private AdminService() {}

    public static boolean isAdmin(ServerPlayer p) {
        return Commands.LEVEL_GAMEMASTERS.check(p.permissions());
    }

    public static void open(ServerPlayer p) {
        if (!Send.canOpen(p)) return;
        Sessions.open(p, "admin", null, null, null);
        send(p, "", false);
    }

    public static void send(ServerPlayer p, String msg, boolean error) {
        Views.View v = Send.base(p, "admin", msg, error);
        if (!isAdmin(p)) {
            v.allowed = false;
            v.message = "Nur für Admins.";
            Send.send(p, v);
            return;
        }
        long now = MarktRuntime.now();
        MarktData d = MarktRuntime.data();
        MarktConfig c = MarktRuntime.config();
        for (Plot plot : d.plots.values()) v.plots.add(StallService.line(plot, "", now));
        for (MarktData.Zone z : d.zones.values()) {
            Views.ZoneLine l = new Views.ZoneLine();
            l.id = z.id;
            l.name = z.name;
            l.size = z.box.size();
            l.pos = z.box.toString();
            v.zones.add(l);
        }
        Selections.Sel s = Selections.get(p);
        if (s != null) {
            Views.Selection sel = new Views.Selection();
            sel.a = s.a == null ? "" : s.a.getX() + " " + s.a.getY() + " " + s.a.getZ();
            sel.b = s.b == null ? "" : s.b.getX() + " " + s.b.getY() + " " + s.b.getZ();
            sel.complete = s.complete();
            Box box = s.box();
            if (box != null) {
                sel.size = box.size();
                sel.volume = box.volume();
                for (Plot o : d.plots.values()) if (o.box.intersects(box)) { sel.overlap = o.name; break; }
            }
            v.selection = sel;
        }
        var reg = p.level().registryAccess();
        for (MarktConfig.BuyerItem bi : c.buyer.items) {
            Item item = Stacks.item(bi.item);
            Views.BuyerLine l = new Views.BuyerLine();
            l.item = item == null ? "" : Stacks.toString(reg, new ItemStack(item));
            l.name = bi.item;
            l.bundle = bi.bundle;
            l.basePrice = MarketMath.tenths(bi.price);
            l.price = BuyerService.price(bi);
            l.demand = bi.demand;
            l.left = bi.dailyLimit;
            l.sold = d.sold.getOrDefault(bi.item, 0.0);
            v.buyerItems.add(l);
        }
        for (Job j : d.jobs) {
            Item item = Stacks.item(j.item);
            Views.JobLine l = new Views.JobLine();
            l.id = j.id;
            l.title = j.title;
            l.item = item == null ? "" : Stacks.toString(reg, new ItemStack(item));
            l.itemName = j.item;
            l.count = j.count;
            l.reward = j.reward;
            l.remaining = j.remaining();
            l.slots = j.slots;
            l.system = j.system;
            l.poster = j.system ? "Markt" : j.posterName;
            l.expiresIn = j.expires <= 0 ? 0 : j.expires - now;
            v.jobs.add(l);
        }
        v.jobRefreshIn = Math.max(0, d.lastJobRefresh + c.jobs.refreshMinutes * MarketMath.MINUTE - now);
        Views.Settings st = new Views.Settings();
        st.periodMinutes = c.stalls.periodMinutes;
        st.maxPerPlayer = c.stalls.maxPerPlayer;
        st.maxHelpers = c.stalls.maxHelpers;
        st.maxOffers = c.shop.maxOffers;
        st.feePercent = c.shop.feePercent;
        st.buybackPercent = c.stalls.buybackPercent;
        st.rentRefundPercent = c.stalls.rentRefundPercent;
        st.counterPrice = c.shop.counterPrice;
        st.resetOnRelease = c.stalls.resetOnRelease;
        st.protectFree = c.stalls.protectFree;
        st.allowCash = c.stalls.allowCash;
        st.buyerEnabled = c.buyer.enabled;
        st.buyerPayCash = c.buyer.payCash;
        st.jobsEnabled = c.jobs.enabled;
        st.playerJobs = c.jobs.playerJobs;
        st.snowEnabled = c.snow.enabled;
        st.jobsActive = c.jobs.active;
        st.jobsSlots = c.jobs.slots;
        st.snowPay = c.snow.pay;
        st.snowDailyMax = c.snow.dailyMax;
        st.snowBlocksPerPay = c.snow.blocksPerPay;
        v.settings = st;
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty()) {
            v.hand = Stacks.id(hand);
            v.handName = hand.getHoverName().getString();
        }
        Send.send(p, v);
    }

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, "admin");
            return;
        }
        if (!isAdmin(p)) {
            send(p, "", true);
            return;
        }
        if (Sessions.check(p, "admin") == null) Sessions.open(p, "admin", null, null, null);
        String msg;
        try {
            msg = run(p, act);
        } catch (IllegalArgumentException e) {
            send(p, e.getMessage(), true);
            return;
        }
        if (!msg.isEmpty()) {
            MarktRuntime.markDirty();
            TwoggMarkt.LOGGER.info("Markt-Admin {}: {} {}", p.getGameProfile().name(), act.a, act.plot == null ? "" : act.plot);
        }
        send(p, msg, false);
        if (!msg.isEmpty()) StallService.refreshOpen();
    }

    private static IllegalArgumentException err(String m) {
        return new IllegalArgumentException(m);
    }

    private static Plot plot(Views.Action act) {
        Plot plot = act.plot == null ? null : MarktRuntime.data().plots.get(act.plot);
        if (plot == null) throw err("Diesen Stand gibt es nicht.");
        return plot;
    }

    /** Führt die Aktion aus; Rückgabe = Meldung (leer = nur Ansicht). Fehler als IllegalArgumentException. */
    static String run(ServerPlayer p, Views.Action act) {
        MarktData d = MarktRuntime.data();
        MarktConfig c = MarktRuntime.config();
        String a = act.a == null ? "open" : act.a;
        switch (a) {
            case "open", "refresh" -> { return ""; }
            case "sel_expand" -> {
                Selections.expand(p, Math.max(0, act.count), 0);
                return "Auswahl um " + act.count + " nach oben erweitert.";
            }
            case "sel_clear" -> {
                Selections.clear(p);
                return "Auswahl gelöscht.";
            }
            case "plot_create" -> {
                Plot plot = createPlot(p, act.name, act.value, act.amount / 10.0);
                return "Stand „" + plot.name + "“ angelegt (" + plot.box.size() + ")" + (Snapshots.exists(plot.id) ? ", Leerzustand gesichert." : ".");
            }
            case "zone_create" -> {
                Selections.Sel s = Selections.get(p);
                if (s == null || !s.complete()) throw err("Erst mit dem Marktplaner zwei Ecken setzen.");
                String name = act.name == null || act.name.isBlank() ? "Gasse " + (d.zones.size() + 1) : act.name.trim();
                String id = uniqueId(MarketMath.slug(name), d.zones.keySet());
                MarktData.Zone z = new MarktData.Zone();
                z.id = id;
                z.name = name;
                z.box = s.box();
                d.zones.put(id, z);
                Selections.clear(p);
                return "Räumzone „" + name + "“ angelegt.";
            }
            case "zone_delete" -> {
                if (d.zones.remove(act.id == null ? "" : act.id) == null) throw err("Diese Zone gibt es nicht.");
                return "Räumzone gelöscht.";
            }
            case "plot_delete" -> {
                Plot plot = plot(act);
                if (plot.active(MarktRuntime.now())) StallService.release(plot, "von der Marktleitung aufgelöst");
                d.plots.remove(plot.id);
                Snapshots.delete(plot.id);
                return "Stand „" + plot.name + "“ gelöscht.";
            }
            case "plot_evict" -> {
                Plot plot = plot(act);
                if (!plot.hasOwner()) throw err("Der Stand ist nicht vergeben.");
                StallService.release(plot, "von der Marktleitung geräumt");
                return "Stand „" + plot.name + "“ geräumt.";
            }
            case "plot_reset" -> {
                Plot plot = plot(act);
                if (!Snapshots.exists(plot.id)) throw err("Für diesen Stand gibt es keinen Leerzustand.");
                if (!Snapshots.restore(plot)) throw err("Zurücksetzen fehlgeschlagen (siehe Log).");
                return "Stand „" + plot.name + "“ zurückgesetzt.";
            }
            case "plot_snapshot" -> {
                Plot plot = plot(act);
                if (!Snapshots.take(plot)) throw err("Sichern fehlgeschlagen (siehe Log).");
                return "Leerzustand von „" + plot.name + "“ neu gesichert.";
            }
            case "plot_mode" -> {
                Plot plot = plot(act);
                Plot.Mode m;
                try {
                    m = Plot.Mode.valueOf(act.text == null ? "" : act.text.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw err("Unbekannte Art.");
                }
                plot.mode = m;
                return "„" + plot.name + "“: " + modeName(m) + ".";
            }
            case "plot_rent" -> {
                Plot plot = plot(act);
                if (act.value < 0) throw err("Preis darf nicht negativ sein.");
                plot.rentPrice = act.value;
                return "Miete „" + plot.name + "“: " + euro(act.value) + " pro " + c.stalls.periodName + ".";
            }
            case "plot_buy" -> {
                Plot plot = plot(act);
                if (act.value < 0) throw err("Preis darf nicht negativ sein.");
                plot.buyPrice = act.value;
                return "Kaufpreis „" + plot.name + "“: " + euro(act.value) + ".";
            }
            case "plot_periods" -> {
                Plot plot = plot(act);
                plot.maxPeriods = Math.max(1, Math.min(365, act.count));
                return "„" + plot.name + "“: höchstens " + MarktRuntime.periods(plot.maxPeriods) + " im Voraus.";
            }
            case "plot_name" -> {
                Plot plot = plot(act);
                if (act.text == null || act.text.isBlank()) throw err("Name eingeben.");
                plot.name = act.text.trim().length() > 32 ? act.text.trim().substring(0, 32) : act.text.trim();
                return "Umbenannt in „" + plot.name + "“.";
            }
            case "plot_note" -> {
                Plot plot = plot(act);
                String n = act.text == null ? "" : act.text.trim();
                plot.note = n.length() > 60 ? n.substring(0, 60) : n;
                return "Beschreibung gespeichert.";
            }
            case "plot_extend" -> {
                Plot plot = plot(act);
                if (!plot.hasOwner() || plot.bought) throw err("Nur vermietete Stände.");
                plot.rentedUntil = MarketMath.extend(plot.rentedUntil, MarktRuntime.now(), Math.max(1, act.count), MarktRuntime.periodMs());
                return "„" + plot.name + "“ um " + MarktRuntime.periods(Math.max(1, act.count)) + " verlängert (kostenlos).";
            }
            case "plot_tp" -> {
                Plot plot = plot(act);
                Identifier id = Identifier.tryParse(plot.box.dim);
                ServerLevel level = id == null ? null : p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
                if (level == null) throw err("Dimension nicht geladen.");
                p.teleportTo(level, plot.box.centerX(), plot.box.minY + 1, plot.box.centerZ(), Set.<Relative>of(), p.getYRot(), p.getXRot(), true);
                return "";
            }
            case "plot_show" -> {
                Plot plot = plot(act);
                Outlines.show(p, plot.id, 15_000);
                return "";
            }
            case "buyer_add" -> {
                ItemStack hand = p.getMainHandItem();
                if (hand.isEmpty()) throw err("Das Item, das der Markt kaufen soll, in die Hand nehmen.");
                String id = Stacks.id(hand);
                if (c.buyerItem(id) != null) throw err("Das kauft der Markt schon.");
                if (act.value <= 0) throw err("Preis in Coins eingeben.");
                c.buyer.items.add(new MarktConfig.BuyerItem(id, "", Math.max(1, act.count), act.value, 64, 32));
                MarktRuntime.saveConfig();
                return "Ankauf: " + Math.max(1, act.count) + "× " + hand.getHoverName().getString() + " für " + act.value + " Coins.";
            }
            case "buyer_edit" -> {
                MarktConfig.BuyerItem bi = c.buyerItem(act.id == null ? "" : act.id);
                if (bi == null) throw err("Unbekannter Ankaufsposten.");
                if (act.value > 0) bi.price = act.value;
                if (act.count > 0) bi.bundle = act.count;
                if (act.amount > 0) bi.demand = (int) act.amount;
                MarktRuntime.saveConfig();
                return "Ankauf " + bi.item + ": " + bi.bundle + " Stück für " + bi.price + " Coins, Bedarf " + bi.demand + ".";
            }
            case "buyer_remove" -> {
                if (!c.buyer.items.removeIf(i -> i.item.equals(act.id))) throw err("Unbekannter Ankaufsposten.");
                MarktRuntime.saveConfig();
                return "Ankaufsposten entfernt.";
            }
            case "jobs_reroll" -> {
                BoardService.reroll();
                return "Neue Marktaufträge ausgehängt.";
            }
            case "job_remove" -> {
                Job j = d.job(act.id == null ? "" : act.id);
                if (j == null) throw err("Diesen Auftrag gibt es nicht.");
                d.jobs.remove(j);
                if (!j.system && j.remaining() > 0) Pay.later(j.poster, j.posterName, j.reward * j.remaining(), "Auftrag entfernt: " + j.title);
                return "Auftrag entfernt" + (j.system ? "." : ", Belohnung geht an " + j.posterName + " zurück.");
            }
            case "setting" -> {
                String r = setting(c, act.id == null ? "" : act.id, act.value, act.flag);
                MarktRuntime.saveConfig();
                return r;
            }
            case "reload" -> {
                if (!MarktRuntime.reload()) throw err("Einstellungen fehlerhaft, nichts geändert (siehe Log).");
                return "Einstellungen neu geladen.";
            }
            default -> { return ""; }
        }
    }

    public static Plot createPlot(ServerPlayer p, String name, double rent, double buy) {
        MarktData d = MarktRuntime.data();
        MarktConfig c = MarktRuntime.config();
        Selections.Sel s = Selections.get(p);
        if (s == null || !s.complete()) throw err("Erst mit dem Marktplaner zwei Ecken setzen.");
        Box box = s.box();
        if (box.volume() > c.stalls.maxVolume) throw err("Zu groß: " + box.volume() + " Blöcke (höchstens " + c.stalls.maxVolume + ").");
        for (Plot o : d.plots.values()) if (o.box.intersects(box)) throw err("Überschneidet sich mit „" + o.name + "“.");
        String nm = name == null || name.isBlank() ? "Stand " + (d.plots.size() + 1) : name.trim();
        if (nm.length() > 32) nm = nm.substring(0, 32);
        String id = uniqueId(MarketMath.slug(nm), d.plots.keySet());
        Plot plot = new Plot();
        plot.id = id;
        plot.name = nm;
        plot.box = box;
        if (rent > 0) plot.rentPrice = rent;
        if (buy > 0) plot.buyPrice = buy;
        d.plots.put(id, plot);
        Snapshots.take(plot);
        Selections.clear(p);
        MarktRuntime.markDirty();
        return plot;
    }

    static String uniqueId(String base, Set<String> taken) {
        String b = base.isEmpty() ? "stand" : base;
        String id = b;
        for (int i = 2; taken.contains(id); i++) id = b + "_" + i;
        return id;
    }

    static String modeName(Plot.Mode m) {
        return switch (m) {
            case RENT -> "nur Miete";
            case BUY -> "nur Kauf";
            case BOTH -> "Miete oder Kauf";
            case LOCKED -> "gesperrt";
        };
    }

    static String euro(double v) {
        return String.format(Locale.GERMANY, "%.2f €", v);
    }

    private static String setting(MarktConfig c, String key, double v, boolean flag) {
        switch (key) {
            case "periodMinutes" -> c.stalls.periodMinutes = (int) Math.max(1, v);
            case "maxPerPlayer" -> c.stalls.maxPerPlayer = (int) Math.max(0, v);
            case "maxHelpers" -> c.stalls.maxHelpers = (int) Math.max(0, v);
            case "maxOffers" -> c.shop.maxOffers = (int) Math.max(1, v);
            case "feePercent" -> c.shop.feePercent = v;
            case "buybackPercent" -> c.stalls.buybackPercent = v;
            case "rentRefundPercent" -> c.stalls.rentRefundPercent = v;
            case "counterPrice" -> c.shop.counterPrice = v;
            case "jobsActive" -> c.jobs.active = (int) v;
            case "jobsSlots" -> c.jobs.slots = (int) v;
            case "snowPay" -> c.snow.pay = v;
            case "snowDailyMax" -> c.snow.dailyMax = v;
            case "snowBlocksPerPay" -> c.snow.blocksPerPay = (int) v;
            case "resetOnRelease" -> c.stalls.resetOnRelease = flag;
            case "protectFree" -> c.stalls.protectFree = flag;
            case "allowCash" -> c.stalls.allowCash = flag;
            case "buyerEnabled" -> c.buyer.enabled = flag;
            case "buyerPayCash" -> c.buyer.payCash = flag;
            case "jobsEnabled" -> c.jobs.enabled = flag;
            case "playerJobs" -> c.jobs.playerJobs = flag;
            case "snowEnabled" -> c.snow.enabled = flag;
            default -> throw err("Unbekannte Einstellung " + key + ".");
        }
        c.sanitize();
        return "Einstellung gespeichert.";
    }
}
