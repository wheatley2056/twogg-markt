package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Ankaufstelle: Der Markt kauft Rohstoffe und Gebäck. Der Preis fällt, je mehr heute schon verkauft wurde
 * (alle Spieler zusammen), und erholt sich mit der Zeit. Pro Spieler gibt es zusätzlich eine Tagesgrenze.
 */
public final class BuyerService {
    private BuyerService() {}

    public static void open(ServerPlayer p, BlockPos pos) {
        if (!Send.canOpen(p)) return;
        if (!MarktRuntime.config().buyer.enabled) {
            p.sendOverlayMessage(Component.literal("Der Ankauf ist geschlossen.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (MarktRuntime.config().event.onlyDuringEvent && !EventService.running()) {
            p.sendOverlayMessage(Component.literal("Der Ankauf hat nur geöffnet, solange der Weihnachtsmarkt läuft.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Pay.settle(p);
        Sessions.open(p, "buyer", pos, ModContent.BUYER, null);
        send(p, "", false);
    }

    /** Abklingen der verkauften Mengen (alle 5 Sekunden). */
    public static void decay() {
        MarktData d = MarktRuntime.data();
        long now = MarktRuntime.now();
        long elapsed = d.soldUpdated <= 0 ? 0 : now - d.soldUpdated;
        d.soldUpdated = now;
        if (elapsed <= 0 || d.sold.isEmpty()) return;
        MarktConfig c = MarktRuntime.config();
        for (Map.Entry<String, Double> e : d.sold.entrySet()) {
            MarktConfig.BuyerItem bi = c.buyerItem(e.getKey());
            int demand = bi == null ? 64 : bi.demand;
            e.setValue(MarketMath.decay(e.getValue(), demand, elapsed, c.buyer.recoverMinutes));
        }
        d.sold.values().removeIf(v -> v <= 0.001);
    }

    static long price(MarktConfig.BuyerItem bi) {
        double sold = MarktRuntime.data().sold.getOrDefault(bi.item, 0.0);
        return MarketMath.buyerPrice(bi.price, sold, bi.demand, MarktRuntime.config().buyer.minFactor);
    }

    static void send(ServerPlayer p, String msg, boolean error) {
        MarktConfig c = MarktRuntime.config();
        Views.View v = Send.base(p, "buyer", msg, error);
        v.title = "Ankauf";
        v.subtitle = c.title + " · Preise fallen, je mehr heute verkauft wird";
        v.payCash = c.buyer.payCash;
        MarktData.Daily daily = MarktRuntime.data().daily(p.getUUID().toString());
        var reg = p.level().registryAccess();
        for (MarktConfig.BuyerItem bi : c.buyer.items) {
            Item item = Stacks.item(bi.item);
            if (item == null) continue;
            Views.BuyerLine l = new Views.BuyerLine();
            l.item = Stacks.toString(reg, new ItemStack(item));
            l.name = bi.name.isBlank() ? new ItemStack(item).getHoverName().getString() : bi.name;
            l.bundle = bi.bundle;
            l.basePrice = MarketMath.tenths(bi.price);
            l.price = price(bi);
            l.have = Inv.count(p, Inv.item(item)) / bi.bundle;
            l.left = bi.dailyLimit <= 0 ? -1 : Math.max(0, bi.dailyLimit - daily.bought.getOrDefault(bi.item, 0));
            l.demand = bi.demand;
            l.sold = MarktRuntime.data().sold.getOrDefault(bi.item, 0.0);
            v.buyerItems.add(l);
        }
        Send.send(p, v);
    }

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, "buyer");
            return;
        }
        if (Sessions.check(p, "buyer") == null) {
            p.sendOverlayMessage(Component.literal("Du stehst nicht mehr am Ankauf.").withStyle(ChatFormatting.RED));
            return;
        }
        if ("refresh".equals(act.a)) {
            if (!Sessions.tooSoon(p)) send(p, "", false);
            return;
        }
        if (!"sell".equals(act.a)) return;
        MarktConfig c = MarktRuntime.config();
        String itemId = act.id == null ? "" : act.id;
        MarktConfig.BuyerItem bi = c.buyerItem(itemId);
        Item item = bi == null ? null : Stacks.item(bi.item);
        if (item == null || !c.buyer.enabled) {
            send(p, "Das kauft der Markt gerade nicht.", true);
            return;
        }
        MarktData.Daily daily = MarktRuntime.data().daily(p.getUUID().toString());
        int have = Inv.count(p, Inv.item(item)) / bi.bundle;
        int want = act.flag ? have : 1;
        if (bi.dailyLimit > 0) want = Math.min(want, bi.dailyLimit - daily.bought.getOrDefault(bi.item, 0));
        if (have <= 0) { send(p, "Du hast nicht genug dabei (" + bi.bundle + " Stück pro Ankauf).", true); return; }
        if (want <= 0) { send(p, "Für heute hast du davon genug verkauft.", true); return; }
        // Bündel einzeln rechnen: der Preis fällt mit jedem verkauften Bündel
        double sold = MarktRuntime.data().sold.getOrDefault(bi.item, 0.0);
        long total = 0;
        int n = 0;
        for (; n < want; n++) {
            long each = MarketMath.buyerPrice(bi.price, sold, bi.demand, c.buyer.minFactor);
            if (each <= 0) break;
            total += each;
            sold += 1;
        }
        if (n == 0 || total <= 0) { send(p, "Dafür zahlt der Markt heute nichts mehr.", true); return; }
        Inv.take(p, Inv.item(item), n * bi.bundle);
        MarktRuntime.data().sold.put(bi.item, sold);
        if (MarktRuntime.data().soldUpdated <= 0) MarktRuntime.data().soldUpdated = MarktRuntime.now();
        daily.bought.merge(bi.item, n, Integer::sum);
        MarktRuntime.markDirty();
        String name = bi.name.isBlank() ? new ItemStack(item).getHoverName().getString() : bi.name;
        Pay.payout(p, total, c.buyer.payCash, "markt:ankauf", "Ankauf " + (n * bi.bundle) + "× " + name);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        send(p, (n * bi.bundle) + "× " + name + " verkauft: " + MarktRuntime.fmt(total) + (c.buyer.payCash ? " bar." : " aufs Konto."), false);
    }
}
