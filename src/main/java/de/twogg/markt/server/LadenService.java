package de.twogg.markt.server;

import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.Laden;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Offer;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Server-Laden (seit 0.4.0): feste Preise, unbegrenzter Vorrat, Verkauf und Ankauf. Kein Block: Geöffnet wird per
 * {@code /markt laden oeffnen <id> <spieler>} (Storyteller-NPC, Befehlsblock …); das Fenster gilt, solange der Spieler
 * höchstens 8 Blöcke von der Stelle entfernt bleibt, an der es aufging.
 */
public final class LadenService {
    private LadenService() {}

    public static final String SCREEN = "laden";

    /** @return Fehlermeldung oder {@code null} */
    public static String open(ServerPlayer p, String id) {
        Laden l = MarktRuntime.data().laeden.get(id);
        if (l == null) return "Unbekannter Laden: " + id;
        if (!Send.canOpen(p)) return "Dem Spieler fehlt die Mod TwoGG Markt.";
        if (!l.open) {
            p.sendOverlayMessage(Component.literal(l.name + " hat gerade geschlossen.").withStyle(ChatFormatting.GRAY));
            return null;
        }
        Pay.settle(p);
        Sessions.open(p, SCREEN, p.blockPosition(), null, l.id);
        send(p, l, "", false);
        return null;
    }

    static String dailyKey(Laden l, Laden.Buy b) {
        return "laden:" + l.id + ":" + b.id;
    }

    static void send(ServerPlayer p, Laden l, String msg, boolean error) {
        Views.View v = Send.base(p, SCREEN, msg, error);
        v.title = l.name;
        v.subtitle = l.subtitle;
        v.payCash = MarktRuntime.config().buyer.payCash;
        var reg = p.level().registryAccess();
        for (Offer o : l.sells) {
            ItemStack proto = Stacks.fromJson(reg, o.item);
            Views.OfferLine line = new Views.OfferLine();
            line.id = o.id;
            line.item = proto.isEmpty() ? "" : Stacks.toString(reg, proto);
            line.name = proto.isEmpty() ? o.name + " (unbekannt)" : o.name;
            line.count = o.count;
            line.price = o.price;
            line.sold = o.sold;
            line.stock = proto.isEmpty() ? 0 : -1; // -1 = unbegrenzt
            v.offers.add(line);
        }
        MarktData.Daily daily = MarktRuntime.data().daily(p.getUUID().toString());
        for (Laden.Buy b : l.buys) {
            ItemStack proto = Stacks.fromJson(reg, b.item);
            if (proto.isEmpty()) continue;
            Views.BuyerLine line = new Views.BuyerLine();
            line.id = b.id;
            line.item = Stacks.toString(reg, proto);
            line.name = b.name;
            line.bundle = b.count;
            line.price = b.price;
            line.basePrice = b.price;
            line.have = Inv.count(p, Inv.item(proto.getItem())) / Math.max(1, b.count);
            line.left = b.dailyLimit <= 0 ? -1 : Math.max(0, b.dailyLimit - daily.bought.getOrDefault(dailyKey(l, b), 0));
            v.buyerItems.add(line);
        }
        Send.send(p, v);
    }

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, SCREEN, act.session);
            return;
        }
        Sessions.Session s = Sessions.check(p, SCREEN);
        Laden l = s == null ? null : MarktRuntime.data().laeden.get(s.plot());
        if (l == null || !l.open) {
            Sessions.close(p, SCREEN, 0);
            p.sendOverlayMessage(Component.literal("Du stehst nicht mehr am Laden.").withStyle(ChatFormatting.RED));
            return;
        }
        switch (act.a) {
            case "refresh" -> {
                if (!Sessions.tooSoon(p)) send(p, l, "", false);
            }
            case "buy" -> {
                String[] r = buy(p, l, act.id, Math.max(1, Math.min(64, act.count)), act.cash);
                send(p, l, r[0], r[1] != null);
            }
            case "sell" -> {
                String[] r = sell(p, l, act.id, act.flag);
                send(p, l, r[0], r[1] != null);
            }
            default -> { }
        }
    }

    /** @return {Meldung, null = ok / sonst Fehler} */
    static String[] buy(ServerPlayer p, Laden l, String offerId, int times, boolean cash) {
        Offer o = l.sell(offerId);
        if (o == null) return new String[]{"Das Angebot gibt es nicht mehr.", "x"};
        var reg = p.level().registryAccess();
        ItemStack proto = Stacks.fromJson(reg, o.item);
        if (proto.isEmpty()) return new String[]{"Diese Ware gibt es auf dem Server nicht mehr.", "x"};
        long total = o.price * times;
        TxResult tx = Pay.charge(p, total, cash, "markt:laden", times + "× " + o.name + " bei " + l.name);
        if (!tx.ok()) return new String[]{tx.message(), "x"};
        int left = o.count * times;
        int max = Math.max(1, proto.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            Inv.give(p, proto.copyWithCount(n));
            left -= n;
        }
        o.sold += times;
        MarktRuntime.markDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        return new String[]{"Gekauft: " + (o.count * times) + "× " + o.name + " für " + MarktRuntime.fmt(total) + ".", null};
    }

    static String[] sell(ServerPlayer p, Laden l, String buyId, boolean all) {
        Laden.Buy b = l.buy(buyId);
        if (b == null) return new String[]{"Das kauft " + l.name + " nicht an.", "x"};
        ItemStack proto = Stacks.fromJson(p.level().registryAccess(), b.item);
        if (proto.isEmpty()) return new String[]{"Diese Ware gibt es auf dem Server nicht mehr.", "x"};
        int bundle = Math.max(1, b.count);
        int have = Inv.count(p, Inv.item(proto.getItem())) / bundle;
        if (have <= 0) return new String[]{"Du hast nicht genug dabei (" + bundle + " Stück pro Ankauf).", "x"};
        MarktData.Daily daily = MarktRuntime.data().daily(p.getUUID().toString());
        int n = all ? have : 1;
        if (b.dailyLimit > 0) n = Math.min(n, b.dailyLimit - daily.bought.getOrDefault(dailyKey(l, b), 0));
        if (n <= 0) return new String[]{"Für heute nimmt " + l.name + " davon nichts mehr an.", "x"};
        Inv.take(p, Inv.item(proto.getItem()), n * bundle);
        daily.bought.merge(dailyKey(l, b), n, Integer::sum);
        b.bought += n;
        long total = b.price * n;
        MarktRuntime.markDirty();
        Pay.payout(p, total, MarktRuntime.config().buyer.payCash, "markt:laden", "Ankauf " + (n * bundle) + "× " + b.name + " bei " + l.name);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        return new String[]{(n * bundle) + "× " + b.name + " verkauft: " + MarktRuntime.fmt(total)
                + (MarktRuntime.config().buyer.payCash ? " bar." : " aufs Konto."), null};
    }

    // ------------------------------------------------------------------ Verwaltung (Befehle)

    public static String create(String id, String name) {
        if (!id.matches("[a-z0-9_]{1,32}")) return "ID nur aus a–z, 0–9, _ (höchstens 32 Zeichen).";
        if (MarktRuntime.data().laeden.containsKey(id)) return "Den Laden „" + id + "“ gibt es schon.";
        Laden l = new Laden();
        l.id = id;
        l.name = name;
        MarktRuntime.data().laeden.put(id, l);
        MarktRuntime.markDirty();
        return null;
    }

    /** Ware aus der Hand anbieten. @return Meldung */
    public static String addSell(ServerPlayer p, Laden l, double coins, int count) {
        return addSell(p.level().registryAccess(), p.getMainHandItem(), l, coins, count);
    }

    /** Ware {@code hand} (Vorlage, Anzahl egal) anbieten. */
    public static String addSell(net.minecraft.core.HolderLookup.Provider reg, ItemStack hand, Laden l, double coins, int count) {
        if (hand.isEmpty()) return "Nimm die Ware in die Hand (oder gib die Item-ID an).";
        long price = MarketMath.tenths(coins);
        if (price <= 0) return "Preis mindestens 0,1.";
        Offer o = new Offer();
        o.id = "v" + (l.nextOffer++);
        o.item = Stacks.toJson(reg, hand.copyWithCount(1));
        o.name = hand.getHoverName().getString();
        o.count = Math.max(1, Math.min(64 * 9, count));
        o.price = price;
        l.sells.add(o);
        MarktRuntime.markDirty();
        return "Verkauf " + o.id + ": " + o.count + "× " + o.name + " für " + MarktRuntime.fmt(price) + ".";
    }

    /** Ankauf der Item-Art in der Hand. @return Meldung */
    public static String addBuy(ServerPlayer p, Laden l, double coins, int count, int dailyLimit) {
        return addBuy(p.level().registryAccess(), p.getMainHandItem(), l, coins, count, dailyLimit);
    }

    public static String addBuy(net.minecraft.core.HolderLookup.Provider reg, ItemStack hand, Laden l, double coins, int count, int dailyLimit) {
        if (hand.isEmpty()) return "Nimm ein Beispiel der Ware in die Hand (oder gib die Item-ID an).";
        long price = MarketMath.tenths(coins);
        if (price <= 0) return "Preis mindestens 0,1.";
        Laden.Buy b = new Laden.Buy();
        b.id = "a" + (l.nextOffer++);
        b.item = Stacks.toJson(reg, new ItemStack(hand.getItem()));
        b.name = new ItemStack(hand.getItem()).getHoverName().getString();
        b.count = Math.max(1, Math.min(64 * 9, count));
        b.price = price;
        b.dailyLimit = Math.max(0, dailyLimit);
        l.buys.add(b);
        MarktRuntime.markDirty();
        return "Ankauf " + b.id + ": " + b.count + "× " + b.name + " für " + MarktRuntime.fmt(price)
                + (b.dailyLimit > 0 ? " (höchstens " + b.dailyLimit + "× pro Tag)" : "") + ".";
    }

    public static boolean remove(Laden l, String lineId) {
        boolean r = l.sells.removeIf(o -> o.id.equals(lineId)) | l.buys.removeIf(b -> b.id.equals(lineId));
        if (r) MarktRuntime.markDirty();
        return r;
    }
}
