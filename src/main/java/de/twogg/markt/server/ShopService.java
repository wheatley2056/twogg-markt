package de.twogg.markt.server;

import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.Offer;
import de.twogg.markt.core.Plot;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Verkaufstheke: Spieler verkaufen Ware aus den Kisten ihres Stands an andere Spieler. Der Käufer zahlt (Konto oder
 * bar), die Marktgebühr wird vernichtet, der Rest liegt in der Standkasse, bis der Besitzer sie leert.
 */
public final class ShopService {
    private ShopService() {}

    public static void open(ServerPlayer p, BlockPos pos) {
        if (!Send.canOpen(p)) return;
        Plot plot = MarktRuntime.data().plotAt(p.level().dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
        long now = MarktRuntime.now();
        if (plot == null) {
            p.sendOverlayMessage(Component.literal("Diese Theke steht in keinem Stand.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!plot.active(now)) {
            p.sendOverlayMessage(Component.literal("Der Stand „" + plot.name + "“ ist gerade nicht vergeben.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (MarktRuntime.config().event.onlyDuringEvent && !EventService.running() && !plot.member(p.getUUID().toString(), now)) {
            p.sendOverlayMessage(Component.literal("Die Stände verkaufen nur, solange der Weihnachtsmarkt geöffnet ist.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Pay.settle(p);
        Sessions.open(p, "shop", pos, ModContent.COUNTER, plot.id);
        send(p, plot, "", false);
    }

    static void send(ServerPlayer p, Plot plot, String msg, boolean error) {
        MarktConfig c = MarktRuntime.config();
        long now = MarktRuntime.now();
        Views.View v = Send.base(p, "shop", msg, error);
        String me = p.getUUID().toString();
        v.stall = StallService.line(plot, me, now);
        v.owner = plot.isOwner(me, now);
        v.member = plot.member(me, now);
        v.title = plot.name;
        v.subtitle = (plot.ownerName.isEmpty() ? "" : "Stand von " + plot.ownerName) + (plot.shopOpen ? "" : " · geschlossen");
        v.feePercent = c.shop.feePercent;
        v.maxPrice = MarketMath.tenths(c.shop.maxPrice);
        v.maxOffers = c.shop.maxOffers;
        v.counterPrice = MarketMath.priceTenths(c.shop.counterPrice, MarktRuntime.rate());
        v.event = EventService.info(p, false);
        var reg = p.level().registryAccess();
        List<Container> storage = Inv.storage((ServerLevel) p.level(), plot.box);
        for (Offer o : plot.offers) {
            ItemStack proto = Stacks.fromJson(reg, o.item);
            Views.OfferLine l = new Views.OfferLine();
            l.id = o.id;
            l.item = proto.isEmpty() ? "" : Stacks.toString(reg, proto);
            l.name = proto.isEmpty() ? o.name + " (unbekannt)" : proto.getHoverName().getString();
            l.count = o.count;
            l.price = o.price;
            l.sold = o.sold;
            l.stock = proto.isEmpty() ? 0 : Inv.count(storage, Inv.same(proto)) / Math.max(1, o.count);
            v.offers.add(l);
        }
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty() && v.member) {
            v.hand = Stacks.toString(reg, hand.copyWithCount(1));
            v.handName = hand.getHoverName().getString();
        }
        Send.send(p, v);
    }

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, "shop", act.session);
            return;
        }
        Sessions.Session s = Sessions.check(p, "shop");
        Plot plot = s == null ? null : MarktRuntime.data().plots.get(s.plot());
        long now = MarktRuntime.now();
        if (plot == null || !plot.active(now)) {
            Sessions.close(p, "shop", 0);
            p.sendOverlayMessage(Component.literal("Du stehst nicht mehr an der Theke.").withStyle(ChatFormatting.RED));
            return;
        }
        if ("refresh".equals(act.a)) {
            if (!Sessions.tooSoon(p)) send(p, plot, "", false);
            return;
        }
        String me = p.getUUID().toString();
        boolean member = plot.member(me, now);
        MarktConfig c = MarktRuntime.config();
        String msg;
        boolean error = false;
        switch (act.a) {
            case "buy" -> {
                String[] r = buy(p, plot, act.id, Math.max(1, Math.min(64, act.count)), act.cash, act.amount, act.value);
                msg = r[0];
                error = r[1] != null;
            }
            case "offer_add" -> {
                if (!member) { msg = "Das ist nicht dein Stand."; error = true; break; }
                ItemStack hand = p.getMainHandItem();
                long price = act.amount;
                int count = Math.max(1, Math.min(64 * 9, act.count));
                if (hand.isEmpty()) { msg = "Nimm die Ware, die du verkaufen willst, in die Hand."; error = true; }
                else if (hand.is(ModContent.COUNTER_ITEM)) { msg = "Die Theke selbst kannst du nicht anbieten."; error = true; }
                else if (plot.offers.size() >= c.shop.maxOffers) { msg = "Mehr als " + c.shop.maxOffers + " Angebote gehen nicht."; error = true; }
                else if (price <= 0 || price > MarketMath.tenths(c.shop.maxPrice)) {
                    msg = "Preis zwischen 0,1 und " + MarketMath.coins(MarketMath.tenths(c.shop.maxPrice)) + " eingeben."; error = true;
                } else {
                    Offer o = new Offer();
                    o.id = "o" + (plot.nextOffer++);
                    o.item = Stacks.toJson(p.level().registryAccess(), hand.copyWithCount(1));
                    o.name = hand.getHoverName().getString();
                    o.count = count;
                    o.price = price;
                    plot.offers.add(o);
                    MarktRuntime.markDirty();
                    msg = "Angebot: " + count + "× " + o.name + " für " + MarktRuntime.fmt(price) + ". Ware in eine Kiste im Stand legen.";
                }
            }
            case "offer_remove" -> {
                if (!member) { msg = "Das ist nicht dein Stand."; error = true; break; }
                Offer o = plot.offer(act.id);
                if (o == null) { msg = "Das Angebot gibt es nicht mehr."; error = true; }
                else {
                    plot.offers.remove(o);
                    MarktRuntime.markDirty();
                    msg = "Angebot entfernt.";
                }
            }
            case "offer_edit" -> {
                if (!member) { msg = "Das ist nicht dein Stand."; error = true; break; }
                Offer o = plot.offer(act.id);
                long price = act.amount;
                if (o == null) { msg = "Das Angebot gibt es nicht mehr."; error = true; }
                else if (price <= 0 || price > MarketMath.tenths(c.shop.maxPrice)) { msg = "Ungültiger Preis."; error = true; }
                else {
                    o.price = price;
                    if (act.count > 0) o.count = Math.min(64 * 9, act.count);
                    MarktRuntime.markDirty();
                    msg = "Angebot geändert: " + o.count + "× für " + MarktRuntime.fmt(o.price) + ".";
                }
            }
            case "vote" -> {
                String[] r = EventService.vote(p, plot.id);
                msg = r[0];
                error = r[1] != null;
            }
            case "toggle" -> {
                if (!member) { msg = "Das ist nicht dein Stand."; error = true; break; }
                plot.shopOpen = !plot.shopOpen;
                MarktRuntime.markDirty();
                msg = plot.shopOpen ? "Theke geöffnet." : "Theke geschlossen.";
            }
            case "collect" -> {
                if (!plot.isOwner(me, now)) { msg = "Nur der Besitzer leert die Kasse."; error = true; }
                else if (plot.till <= 0) { msg = "Die Kasse ist leer."; error = true; }
                else {
                    long v = plot.till;
                    plot.till = 0;
                    MarktRuntime.markDirty();
                    boolean cash = act.cash && MarktRuntime.config().stalls.allowCash;
                    if (Pay.payout(p, v, cash, "markt:kasse", "Standkasse " + plot.name)) {
                        msg = MarktRuntime.fmt(v) + " aus der Kasse " + (cash ? "bar ausgezahlt." : "aufs Konto.");
                    } else {
                        msg = MarktRuntime.fmt(v) + " aus der Kasse vorgemerkt.";
                    }
                }
            }
            default -> msg = "";
        }
        send(p, plot, msg, error);
    }

    /** @return {Meldung, null = ok / sonst Fehler} */
    /**
     * @param shownPrice im Fenster angezeigter Preis je Kauf (Zehntel), 0 = unbekannt (ältere Clients)
     * @param shownCount im Fenster angezeigte Stückzahl je Kauf, 0 = unbekannt
     */
    private static String[] buy(ServerPlayer p, Plot plot, String offerId, int times, boolean cash, long shownPrice, double shownCount) {
        long now = MarktRuntime.now();
        if (!plot.shopOpen) return new String[]{"Die Theke ist geschlossen.", "x"};
        if (plot.member(p.getUUID().toString(), now)) return new String[]{"Am eigenen Stand kaufst du nicht.", "x"};
        Offer o = plot.offer(offerId);
        if (o == null) return new String[]{"Das Angebot gibt es nicht mehr.", "x"};
        // Lockvogel: Angebot zwischen Anzeige und Kauf verteuert oder verkleinert → nicht kaufen (seit 0.5.2)
        if (MarketMath.offerWorse(shownPrice, shownCount, o.price, o.count)) {
            return new String[]{"Das Angebot hat sich geändert: jetzt " + o.count + "× für " + MarktRuntime.fmt(o.price) + ". Nichts gekauft.", "x"};
        }
        var reg = p.level().registryAccess();
        ItemStack proto = Stacks.fromJson(reg, o.item);
        if (proto.isEmpty()) return new String[]{"Diese Ware gibt es auf dem Server nicht mehr.", "x"};
        List<Container> storage = Inv.storage((ServerLevel) p.level(), plot.box);
        int need = o.count * times;
        if (Inv.count(storage, Inv.same(proto)) < need) return new String[]{"So viel ist nicht mehr da.", "x"};
        long total = o.price * times;
        TxResult tx = Pay.charge(p, total, cash, "markt:stand", times + "× " + o.name + " bei " + plot.name);
        if (!tx.ok()) return new String[]{tx.message(), "x"};
        for (ItemStack s : Inv.take(storage, Inv.same(proto), need)) Inv.give(p, s);
        long fee = MarketMath.fee(total, MarktRuntime.config().shop.feePercent);
        plot.till += total - fee;
        EventService.recordSale(plot.owner, plot.ownerName, total);
        plot.salesTotal += total;
        o.sold += times;
        MarktRuntime.markDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        ServerPlayer op = p.level().getServer().getPlayerList().getPlayer(UUID.fromString(plot.owner));
        if (op != null && op != p) {
            op.sendOverlayMessage(Component.literal(p.getGameProfile().name() + " kauft " + need + "× " + o.name + " – "
                    + MarktRuntime.fmt(total - fee) + " in die Kasse").withStyle(ChatFormatting.GOLD));
        }
        return new String[]{"Gekauft: " + need + "× " + o.name + " für " + MarktRuntime.fmt(total) + ".", null};
    }
}
