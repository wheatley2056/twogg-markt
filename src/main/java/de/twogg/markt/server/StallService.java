package de.twogg.markt.server;

import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.Plot;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stände mieten, kaufen, verlängern, kündigen; Kasse, Helfer; Ablauf und Warnungen. Fenster „market“. */
public final class StallService {
    private StallService() {}

    // ------------------------------------------------------------------ Öffnen

    /** Rechtsklick aufs Standschild: der Stand, in dem (oder direkt neben dem) das Schild steht. */
    public static void openSign(ServerPlayer p, BlockPos pos) {
        Plot plot = plotNear(p.level().dimension().identifier().toString(), pos);
        if (plot == null) {
            p.sendOverlayMessage(Component.literal("Dieses Schild gehört zu keinem Stand.").withStyle(ChatFormatting.RED));
            return;
        }
        open(p, plot.id);
    }

    public static Plot plotNear(String dim, BlockPos pos) {
        Plot in = MarktRuntime.data().plotAt(dim, pos.getX(), pos.getY(), pos.getZ());
        if (in != null) return in;
        Plot best = null;
        double bestD = 9;
        for (Plot p : MarktRuntime.data().plots.values()) {
            Box b = p.box;
            if (!b.dim.equals(dim) || pos.getY() < b.minY - 3 || pos.getY() > b.maxY + 3) continue;
            double dx = Math.max(0, Math.max(b.minX - pos.getX(), pos.getX() - b.maxX));
            double dz = Math.max(0, Math.max(b.minZ - pos.getZ(), pos.getZ() - b.maxZ));
            double d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    public static void open(ServerPlayer p, String focus) {
        if (!Send.canOpen(p)) return;
        Pay.settle(p);
        Sessions.open(p, "market", null, null, focus);
        send(p, "", false, focus == null || focus.isEmpty() ? "" : "stalls");
    }

    static void send(ServerPlayer p, String msg, boolean error, String page) {
        Views.View v = Send.base(p, "market", msg, error);
        v.page = page == null ? "" : page;
        Sessions.Session s = Sessions.get(p);
        v.focus = s != null && s.plot() != null ? s.plot() : "";
        long now = MarktRuntime.now();
        String me = p.getUUID().toString();
        List<Plot> list = new ArrayList<>(MarktRuntime.data().plots.values());
        list.sort(Comparator.comparing((Plot x) -> !x.member(me, now)).thenComparing(x -> x.name.toLowerCase()));
        for (Plot plot : list) v.plots.add(line(plot, me, now));
        v.maxPerPlayer = MarktRuntime.config().stalls.maxPerPlayer;
        v.ownedCount = owned(me, now);
        v.event = EventService.info(p, false);
        Send.send(p, v);
    }

    /** Offene Markt-Fenster neu befüllen (nach Änderungen durch andere). */
    public static void refreshOpen() {
        MinecraftServer server = MarktRuntime.server();
        if (server == null) return;
        for (Map.Entry<UUID, Sessions.Session> e : List.copyOf(Sessions.all().entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            switch (e.getValue().screen()) {
                case "market" -> send(p, "", false, "");
                case "admin" -> AdminService.send(p, "", false);
                default -> { }
            }
        }
    }

    public static Views.PlotLine line(Plot plot, String viewer, long now) {
        MarktConfig c = MarktRuntime.config();
        Views.PlotLine l = new Views.PlotLine();
        l.id = plot.id;
        l.name = plot.name;
        l.note = plot.note;
        l.status = plot.status(now).name();
        l.mode = plot.mode.name();
        l.owner = plot.active(now) ? plot.owner : "";
        l.ownerName = plot.active(now) ? plot.ownerName : "";
        l.size = plot.box.size();
        l.area = plot.box.area();
        l.pos = (int) Math.floor(plot.box.centerX()) + " " + plot.box.minY + " " + (int) Math.floor(plot.box.centerZ());
        l.dim = plot.box.dim;
        l.rentEuro = plot.rentPrice;
        l.buyEuro = plot.buyPrice;
        l.rentTenths = MarketMath.priceTenths(plot.rentPrice, MarktRuntime.rate());
        l.buyTenths = MarketMath.priceTenths(plot.buyPrice, MarktRuntime.rate());
        l.maxPeriods = plot.maxPeriods;
        boolean mine = plot.isOwner(viewer, now);
        l.mine = mine;
        l.helper = !mine && plot.member(viewer, now);
        l.bookable = plot.active(now) && !mine ? 0 : MarketMath.bookable(mine ? plot.rentedUntil : 0, now, plot.maxPeriods, MarktRuntime.periodMs());
        l.remaining = plot.status(now) == Plot.Status.RENTED ? plot.rentedUntil - now : 0;
        if (mine || l.helper) {
            l.helpers.addAll(plot.helpers.values());
            l.till = plot.till;
            l.salesTotal = plot.salesTotal;
        }
        l.offers = plot.offers.size();
        l.shopOpen = plot.shopOpen;
        if (mine) l.refund = refund(plot, now, c);
        l.hasSnapshot = Snapshots.exists(plot.id);
        return l;
    }

    static int owned(String uuid, long now) {
        int n = 0;
        for (Plot p : MarktRuntime.data().plots.values()) if (p.isOwner(uuid, now)) n++;
        return n;
    }

    /** Was man beim Kündigen bzw. Zurückgeben bekäme. */
    static long refund(Plot plot, long now, MarktConfig c) {
        if (plot.bought) return MarketMath.percent(plot.boughtFor, c.stalls.buybackPercent);
        int unused = MarketMath.unusedPeriods(plot.rentedUntil, now, MarktRuntime.periodMs());
        return MarketMath.percent(unused * plot.periodPaid, c.stalls.rentRefundPercent);
    }

    // ------------------------------------------------------------------ Aktionen

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, "market", act.session);
            return;
        }
        if (Sessions.check(p, "market") == null) {
            p.sendOverlayMessage(Component.literal("Das Marktfenster ist nicht mehr offen.").withStyle(ChatFormatting.RED));
            return;
        }
        if ("refresh".equals(act.a)) {
            if (!Sessions.tooSoon(p)) send(p, "", false, "");
            return;
        }
        Plot plot = act.plot == null ? null : MarktRuntime.data().plots.get(act.plot);
        if (plot == null) {
            send(p, "Diesen Stand gibt es nicht mehr.", true, "");
            return;
        }
        Result r = switch (act.a) {
            case "rent" -> rent(p, plot, Math.max(1, act.count), act.cash);
            case "buy" -> buy(p, plot, act.cash);
            case "cancel" -> cancel(p, plot);
            case "collect" -> collect(p, plot);
            case "helper_add" -> helperAdd(p, plot, act.name);
            case "helper_remove" -> helperRemove(p, plot, act.name);
            case "shop_toggle" -> shopToggle(p, plot);
            case "buy_counter" -> buyCounter(p, plot, act.cash);
            case "vote" -> {
                String[] vr = EventService.vote(p, plot.id);
                yield new Result(vr[0], vr[1] != null, "");
            }
            default -> new Result("", false, "");
        };
        send(p, r.msg, r.error, r.page);
        if (!r.error && !r.msg.isEmpty()) refreshOthers(p);
    }

    record Result(String msg, boolean error, String page) {
        static Result fail(String m) { return new Result(m, true, ""); }
        static Result ok(String m) { return new Result(m, false, ""); }
    }

    private static void refreshOthers(ServerPlayer except) {
        MinecraftServer server = MarktRuntime.server();
        for (Map.Entry<UUID, Sessions.Session> e : List.copyOf(Sessions.all().entrySet())) {
            if (e.getKey().equals(except.getUUID())) continue;
            ServerPlayer o = server.getPlayerList().getPlayer(e.getKey());
            if (o == null) continue;
            if ("market".equals(e.getValue().screen())) send(o, "", false, "");
            else if ("admin".equals(e.getValue().screen())) AdminService.send(o, "", false);
        }
    }

    private static Result rent(ServerPlayer p, Plot plot, int count, boolean cash) {
        long now = MarktRuntime.now();
        String me = p.getUUID().toString();
        MarktConfig c = MarktRuntime.config();
        if (!plot.rentable()) return Result.fail("Dieser Stand ist nicht zu mieten.");
        boolean mine = plot.isOwner(me, now);
        if (plot.active(now) && !mine) return Result.fail("Der Stand ist schon vergeben.");
        if (mine && plot.bought) return Result.fail("Der Stand gehört dir schon.");
        if (!mine && c.stalls.maxPerPlayer > 0 && owned(me, now) >= c.stalls.maxPerPlayer) {
            return Result.fail("Du hast schon " + (c.stalls.maxPerPlayer == 1 ? "einen Stand" : c.stalls.maxPerPlayer + " Stände") + ".");
        }
        int max = MarketMath.bookable(mine ? plot.rentedUntil : 0, now, plot.maxPeriods, MarktRuntime.periodMs());
        if (max <= 0) return Result.fail("Weiter im Voraus geht nicht (höchstens " + MarktRuntime.periods(plot.maxPeriods) + ").");
        count = Math.min(count, max);
        long per = MarketMath.priceTenths(plot.rentPrice, MarktRuntime.rate());
        long total = per * count;
        String what = (mine ? "Verlängerung " : "Miete ") + plot.name + " (" + MarktRuntime.periods(count) + ")";
        TxResult tx = Pay.charge(p, total, cash, "markt:miete", what);
        if (!tx.ok()) return Result.fail(tx.message());
        if (!mine) {
            plot.release();
            plot.owner = me;
            plot.ownerName = p.getGameProfile().name();
            plot.since = now;
            giveCounter(p);
        }
        plot.rentedUntil = MarketMath.extend(mine ? plot.rentedUntil : 0, now, count, MarktRuntime.periodMs());
        plot.periodPaid = per;
        plot.warned = 0;
        MarktRuntime.markDirty();
        coin(p);
        TwoggMarkt.LOGGER.info("{} mietet {} für {} ({})", p.getGameProfile().name(), plot.id, MarktRuntime.periods(count), MarktRuntime.fmt(total));
        return new Result((mine ? "Verlängert: " : "Gemietet: ") + plot.name + " für " + MarktRuntime.periods(count) + " – "
                + MarktRuntime.fmt(total) + (mine ? "" : ". Eine Verkaufstheke liegt in deinem Inventar."), false, mine ? "" : "mine");
    }

    private static Result buy(ServerPlayer p, Plot plot, boolean cash) {
        long now = MarktRuntime.now();
        String me = p.getUUID().toString();
        MarktConfig c = MarktRuntime.config();
        if (!plot.buyable()) return Result.fail("Dieser Stand ist nicht zu kaufen.");
        boolean mine = plot.isOwner(me, now);
        if (plot.active(now) && !mine) return Result.fail("Der Stand ist schon vergeben.");
        if (mine && plot.bought) return Result.fail("Der Stand gehört dir schon.");
        if (!mine && c.stalls.maxPerPlayer > 0 && owned(me, now) >= c.stalls.maxPerPlayer) {
            return Result.fail("Du hast schon " + (c.stalls.maxPerPlayer == 1 ? "einen Stand" : c.stalls.maxPerPlayer + " Stände") + ".");
        }
        long price = MarketMath.priceTenths(plot.buyPrice, MarktRuntime.rate());
        TxResult tx = Pay.charge(p, price, cash, "markt:kauf", "Kauf " + plot.name);
        if (!tx.ok()) return Result.fail(tx.message());
        if (!mine) {
            plot.release();
            plot.owner = me;
            plot.ownerName = p.getGameProfile().name();
            plot.since = now;
            giveCounter(p);
        }
        plot.bought = true;
        plot.boughtFor = price;
        plot.rentedUntil = 0;
        plot.periodPaid = 0;
        plot.warned = 0;
        MarktRuntime.markDirty();
        coin(p);
        TwoggMarkt.LOGGER.info("{} kauft {} für {}", p.getGameProfile().name(), plot.id, MarktRuntime.fmt(price));
        return new Result("Gekauft: " + plot.name + " für " + MarktRuntime.fmt(price) + "."
                + (mine ? " Die restliche Miete verfällt." : " Eine Verkaufstheke liegt in deinem Inventar."), false, "mine");
    }

    private static Result cancel(ServerPlayer p, Plot plot) {
        long now = MarktRuntime.now();
        if (!plot.isOwner(p.getUUID().toString(), now)) return Result.fail("Das ist nicht dein Stand.");
        long refund = refund(plot, now, MarktRuntime.config());
        boolean bought = plot.bought;
        String name = plot.name;
        release(plot, bought ? "zurückgegeben" : "gekündigt");
        if (refund > 0) Pay.payout(p, refund, false, bought ? "markt:rueckkauf" : "markt:erstattung",
                (bought ? "Rückkauf " : "Erstattung Miete ") + name);
        return new Result(name + (bought ? " zurückgegeben" : " gekündigt")
                + (refund > 0 ? ", " + MarktRuntime.fmt(refund) + " zurück." : "."), false, "stalls");
    }

    private static Result collect(ServerPlayer p, Plot plot) {
        long now = MarktRuntime.now();
        if (!plot.isOwner(p.getUUID().toString(), now)) return Result.fail("Nur der Besitzer leert die Kasse.");
        if (plot.till <= 0) return Result.fail("Die Kasse ist leer.");
        long v = plot.till;
        plot.till = 0;
        MarktRuntime.markDirty();
        boolean paid = Pay.payout(p, v, false, "markt:kasse", "Standkasse " + plot.name);
        coin(p);
        return Result.ok(MarktRuntime.fmt(v) + (paid ? " aus der Kasse aufs Konto." : " aus der Kasse vorgemerkt."));
    }

    static Result helperAdd(ServerPlayer p, Plot plot, String name) {
        long now = MarktRuntime.now();
        if (!plot.isOwner(p.getUUID().toString(), now)) return Result.fail("Nur der Besitzer wählt Helfer.");
        if (plot.helpers.size() >= MarktRuntime.config().stalls.maxHelpers) return Result.fail("Mehr Helfer gehen nicht.");
        ServerPlayer h = name == null ? null : p.level().getServer().getPlayerList().getPlayerByName(name.trim());
        if (h == null) return Result.fail("Niemand mit diesem Namen ist gerade da.");
        if (h == p) return Result.fail("Du bist schon der Besitzer.");
        plot.helpers.put(h.getUUID().toString(), h.getGameProfile().name());
        MarktRuntime.markDirty();
        h.sendSystemMessage(Component.literal(p.getGameProfile().name() + " hat dich als Helfer für den Stand „"
                + plot.name + "“ eingetragen.").withStyle(ChatFormatting.GREEN));
        return Result.ok(h.getGameProfile().name() + " hilft jetzt am Stand.");
    }

    static Result helperRemove(ServerPlayer p, Plot plot, String name) {
        long now = MarktRuntime.now();
        if (!plot.isOwner(p.getUUID().toString(), now)) return Result.fail("Nur der Besitzer wählt Helfer.");
        String key = null;
        for (Map.Entry<String, String> e : plot.helpers.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name == null ? "" : name.trim())) key = e.getKey();
        }
        if (key == null) return Result.fail("Kein Helfer mit diesem Namen.");
        plot.helpers.remove(key);
        MarktRuntime.markDirty();
        return Result.ok(name + " ist kein Helfer mehr.");
    }

    private static Result shopToggle(ServerPlayer p, Plot plot) {
        if (!plot.member(p.getUUID().toString(), MarktRuntime.now())) return Result.fail("Das ist nicht dein Stand.");
        plot.shopOpen = !plot.shopOpen;
        MarktRuntime.markDirty();
        return Result.ok(plot.shopOpen ? "Die Theke ist geöffnet." : "Die Theke ist geschlossen.");
    }

    private static Result buyCounter(ServerPlayer p, Plot plot, boolean cash) {
        if (!plot.member(p.getUUID().toString(), MarktRuntime.now())) return Result.fail("Das ist nicht dein Stand.");
        long price = MarketMath.priceTenths(MarktRuntime.config().shop.counterPrice, MarktRuntime.rate());
        TxResult tx = Pay.charge(p, price, cash, "markt:theke", "Verkaufstheke");
        if (!tx.ok()) return Result.fail(tx.message());
        giveCounter(p);
        coin(p);
        return Result.ok("Verkaufstheke gekauft" + (price > 0 ? " (" + MarktRuntime.fmt(price) + ")." : "."));
    }

    private static void giveCounter(ServerPlayer p) {
        ItemStack s = new ItemStack(ModContent.COUNTER_ITEM);
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void coin(ServerPlayer p) {
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f);
    }

    // ------------------------------------------------------------------ Ablauf

    /**
     * Stand wird frei: Kasse an den Besitzer (online sofort, sonst beim nächsten Login), auf Wunsch zurücksetzen.
     */
    public static void release(Plot plot, String why) {
        MinecraftServer server = MarktRuntime.server();
        String owner = plot.owner, name = plot.ownerName;
        ServerPlayer op = owner.isEmpty() || server == null ? null : server.getPlayerList().getPlayer(UUID.fromString(owner));
        if (plot.till > 0 && !owner.isEmpty()) {
            if (op != null) Pay.payout(op, plot.till, false, "markt:kasse", "Standkasse " + plot.name);
            else Pay.later(owner, name, plot.till, "Standkasse " + plot.name);
            plot.till = 0;
        }
        if (op != null) {
            op.sendSystemMessage(Component.literal("Dein Stand „" + plot.name + "“ ist " + why + ".").withStyle(ChatFormatting.GOLD));
        }
        TwoggMarkt.LOGGER.info("Stand {} ({}) {}", plot.id, name, why);
        plot.release();
        if (MarktRuntime.config().stalls.resetOnRelease && Snapshots.exists(plot.id)) Snapshots.restore(plot);
        MarktRuntime.markDirty();
    }

    public static void checkExpiry() {
        long now = MarktRuntime.now();
        MinecraftServer server = MarktRuntime.server();
        boolean changed = false;
        for (Plot plot : List.copyOf(MarktRuntime.data().plots.values())) {
            if (!plot.hasOwner() || plot.bought) continue;
            long left = plot.rentedUntil - now;
            if (left <= 0) {
                release(plot, "abgelaufen und wieder frei");
                changed = true;
                continue;
            }
            int stage = left <= 10 * MarketMath.MINUTE ? 2 : left <= 60 * MarketMath.MINUTE ? 1 : 0;
            if (stage > plot.warned) {
                plot.warned = stage;
                MarktRuntime.markDirty();
                ServerPlayer op = server.getPlayerList().getPlayer(UUID.fromString(plot.owner));
                if (op != null) {
                    op.sendSystemMessage(Component.literal("Die Miete für „" + plot.name + "“ läuft in " + MarketMath.duration(left)
                            + " ab. Mit /markt verlängern.").withStyle(ChatFormatting.GOLD));
                }
            }
        }
        if (changed) refreshOpen();
    }
}
