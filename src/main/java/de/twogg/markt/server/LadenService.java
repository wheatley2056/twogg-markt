package de.twogg.markt.server;

import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.Laden;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Offer;
import de.twogg.markt.core.StockMath;
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
        for (Laden.Ware w : l.waren) {
            ItemStack proto = Stacks.fromJson(reg, w.item);
            if (proto.isEmpty()) continue;
            Views.OfferLine line = new Views.OfferLine();
            line.id = w.id;
            line.item = Stacks.toString(reg, proto);
            line.name = w.name;
            line.count = w.unit;
            line.price = StockMath.sellPrice(w.base, w.stock, w.target, w.capacity);
            line.stock = (int) Math.min(Integer.MAX_VALUE, w.stock / Math.max(1, w.unit));
            line.sold = w.sold;
            line.note = stockNote(w);
            v.offers.add(line);
        }
        MarktData.Daily daily = MarktRuntime.data().daily(p.getUUID().toString());
        for (Laden.Ware w : l.waren) {
            ItemStack proto = Stacks.fromJson(reg, w.item);
            if (proto.isEmpty()) continue;
            Views.BuyerLine line = new Views.BuyerLine();
            line.id = w.id;
            line.item = Stacks.toString(reg, proto);
            line.name = w.name;
            line.bundle = w.unit;
            line.price = StockMath.buyPrice(w.base, w.stock, w.target, w.capacity, l.spread);
            line.basePrice = Math.round(w.base * l.spread);
            line.have = Inv.count(p, matcher(proto)) / Math.max(1, w.unit);
            line.left = (int) Math.max(0, (w.capacity - w.stock) / Math.max(1, w.unit));
            line.note = w.stock >= w.capacity ? "Lager voll – nimmt nichts mehr an" : "noch Platz für " + line.left + " · " + stockNote(w);
            v.buyerItems.add(line);
        }
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
                String[] r = l.ware(act.id) != null ? buyWare(p, l, l.ware(act.id), Math.max(1, Math.min(64, act.count)), act.cash)
                        : buy(p, l, act.id, Math.max(1, Math.min(64, act.count)), act.cash);
                send(p, l, r[0], r[1] != null);
            }
            case "sell" -> {
                String[] r = l.ware(act.id) != null ? sellWare(p, l, l.ware(act.id), act.flag) : sell(p, l, act.id, act.flag);
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

    // ------------------------------------------------------------------ Waren mit Lager (seit 0.5.0)

    /** Items mit Daten (Verzauberung …) müssen genau passen, einfache Items nur nach Art. */
    static java.util.function.Predicate<ItemStack> matcher(ItemStack proto) {
        return proto.getComponentsPatch().isEmpty() ? Inv.item(proto.getItem()) : Inv.same(proto);
    }

    static String stockNote(Laden.Ware w) {
        double fill = w.capacity <= 0 ? 0 : (double) w.stock / w.capacity;
        String level = w.stock <= 0 ? "ausverkauft" : fill < 0.2 ? "knapp" : fill < 0.45 ? "wenig" : fill <= 0.55 ? "normal" : fill < 0.85 ? "gut gefüllt" : "voll";
        return "Lager " + level;
    }

    static String[] buyWare(ServerPlayer p, Laden l, Laden.Ware w, int times, boolean cash) {
        var reg = p.level().registryAccess();
        ItemStack proto = Stacks.fromJson(reg, w.item);
        if (proto.isEmpty()) return new String[]{"Diese Ware gibt es auf dem Server nicht mehr.", "x"};
        int unit = Math.max(1, w.unit);
        times = (int) Math.min(times, w.stock / unit);
        if (times <= 0) return new String[]{w.name + " ist ausverkauft – nächste Lieferung abwarten.", "x"};
        long total = StockMath.sellTotal(w.base, w.stock, w.target, w.capacity, unit, times);
        TxResult tx = Pay.charge(p, total, cash, "markt:laden", (times * unit) + "× " + w.name + " bei " + l.name);
        if (!tx.ok()) return new String[]{tx.message(), "x"};
        int left = unit * times;
        int max = Math.max(1, proto.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            Inv.give(p, proto.copyWithCount(n));
            left -= n;
        }
        w.stock -= (long) unit * times;
        w.sold += (long) unit * times;
        MarktRuntime.markDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        return new String[]{"Gekauft: " + (unit * times) + "× " + w.name + " für " + MarktRuntime.fmt(total) + ".", null};
    }

    static String[] sellWare(ServerPlayer p, Laden l, Laden.Ware w, boolean all) {
        ItemStack proto = Stacks.fromJson(p.level().registryAccess(), w.item);
        if (proto.isEmpty()) return new String[]{"Diese Ware gibt es auf dem Server nicht mehr.", "x"};
        int unit = Math.max(1, w.unit);
        int have = Inv.count(p, matcher(proto)) / unit;
        if (have <= 0) return new String[]{"Du hast nicht genug dabei (" + unit + " Stück pro Verkauf).", "x"};
        int space = (int) Math.max(0, (w.capacity - w.stock) / unit);
        if (space <= 0) return new String[]{"Das Lager für " + w.name + " ist voll.", "x"};
        int n = Math.min(all ? have : 1, space);
        // nur so viele, wie noch etwas einbringen
        int paid = 0;
        long total = 0;
        for (; paid < n; paid++) {
            long each = StockMath.buyPrice(w.base, w.stock + (long) paid * unit, w.target, w.capacity, l.spread);
            if (each <= 0) break;
            total += each;
        }
        if (paid <= 0) return new String[]{"Für " + w.name + " zahlt " + l.name + " gerade nichts.", "x"};
        Inv.take(p, matcher(proto), paid * unit);
        w.stock += (long) paid * unit;
        w.bought += (long) paid * unit;
        MarktRuntime.markDirty();
        Pay.payout(p, total, MarktRuntime.config().buyer.payCash, "markt:laden", "Ankauf " + (paid * unit) + "× " + w.name + " bei " + l.name);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        return new String[]{(paid * unit) + "× " + w.name + " verkauft: " + MarktRuntime.fmt(total)
                + (MarktRuntime.config().buyer.payCash ? " bar." : " aufs Konto.") + (paid < (all ? have : 1) ? " Mehr nimmt das Lager nicht." : ""), null};
    }

    /** Letzter planmäßiger Liefertermin ≤ {@code now} (ms). */
    static long lastSlot(Laden l, long now) {
        java.time.ZoneId z;
        try {
            z = java.time.ZoneId.of(l.zone);
        } catch (RuntimeException e) {
            z = java.time.ZoneId.of("Europe/Berlin");
        }
        java.time.DayOfWeek day;
        java.time.LocalTime time;
        try {
            day = java.time.DayOfWeek.valueOf(l.deliveryDay.trim().toUpperCase(java.util.Locale.ROOT));
            time = java.time.LocalTime.parse(l.deliveryTime.trim());
        } catch (RuntimeException e) {
            day = java.time.DayOfWeek.MONDAY;
            time = java.time.LocalTime.of(6, 0);
        }
        java.time.ZonedDateTime n = java.time.Instant.ofEpochMilli(now).atZone(z);
        java.time.ZonedDateTime slot = n.with(java.time.temporal.TemporalAdjusters.previousOrSame(day)).with(time);
        if (slot.isAfter(n)) slot = slot.minusWeeks(1);
        return slot.toInstant().toEpochMilli();
    }

    /** Fällige Lieferungen ausführen (alle 5 s): Bestand auf Soll, Überschuss geht zurück. */
    public static void deliveries() {
        long now = System.currentTimeMillis();
        for (Laden l : MarktRuntime.data().laeden.values()) {
            if (l.waren.isEmpty()) continue;
            long slot = lastSlot(l, now);
            if (l.lastDelivery < slot) {
                deliver(l);
                l.lastDelivery = slot;
                MarktRuntime.markDirty();
            }
        }
    }

    public static void deliver(Laden l) {
        for (Laden.Ware w : l.waren) w.stock = Math.max(0, Math.min(w.capacity, w.target));
        MarktRuntime.markDirty();
    }

    /** Ware anlegen oder ersetzen (gleiche Vorlage). */
    public static String putWare(net.minecraft.core.HolderLookup.Provider reg, Laden l, ItemStack proto, String name, double coins, int unit,
                                 long capacity, long target) {
        if (proto.isEmpty()) return "Keine Ware.";
        long base = MarketMath.tenths(coins);
        if (base <= 0) return "Preis mindestens 0,1.";
        com.google.gson.JsonElement json = Stacks.toJson(reg, proto.copyWithCount(1));
        Laden.Ware w = null;
        for (Laden.Ware x : l.waren) if (json.equals(x.item)) w = x;
        boolean neu = w == null;
        if (neu) {
            w = new Laden.Ware();
            w.id = "w" + (l.nextOffer++);
            w.item = json;
            l.waren.add(w);
        }
        w.name = name == null || name.isBlank() ? proto.getHoverName().getString() : name;
        w.unit = Math.max(1, Math.min(64 * 9, unit));
        w.base = base;
        w.capacity = Math.max(w.unit, capacity);
        w.target = Math.max(0, Math.min(w.capacity, target));
        if (neu) w.stock = w.target;
        w.stock = Math.min(w.stock, w.capacity);
        MarktRuntime.markDirty();
        return (neu ? "Ware " : "Ware geändert ") + w.id + ": " + w.name + " · " + w.unit + " Stück für " + MarktRuntime.fmt(base)
                + " · Lager " + w.capacity + ", Soll " + w.target;
    }

    /**
     * Warenliste aus {@code config/twogg-markt/<datei>.json} laden: {@code {"lieferung": {"tag": "MONDAY", "zeit":
     * "06:00"}, "spread": 0.6, "waren": [{"item": "minecraft:coal", "name": "…", "einheit": 16, "preis": 0.8,
     * "lager": 1024, "soll": 512}, {"stack": {…ItemStack-JSON mit Daten…}, …}]}}. Unbekannte Items werden gemeldet.
     */
    public static java.util.List<String> importFile(net.minecraft.core.HolderLookup.Provider reg, Laden l, String file) {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.nio.file.Path f = de.twogg.markt.data.Store.configFile().getParent().resolve(file.endsWith(".json") ? file : file + ".json");
        com.google.gson.JsonObject root;
        try {
            root = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
        } catch (Exception e) {
            out.add("Datei nicht lesbar: " + f + " (" + e.getMessage() + ")");
            return out;
        }
        if (root.has("spread")) l.spread = root.get("spread").getAsDouble();
        if (root.has("lieferung")) {
            var d = root.getAsJsonObject("lieferung");
            if (d.has("tag")) l.deliveryDay = d.get("tag").getAsString();
            if (d.has("zeit")) l.deliveryTime = d.get("zeit").getAsString();
            if (d.has("zone")) l.zone = d.get("zone").getAsString();
        }
        int ok = 0;
        for (var el : root.getAsJsonArray("waren")) {
            var o = el.getAsJsonObject();
            ItemStack proto;
            String label = o.has("item") ? o.get("item").getAsString() : String.valueOf(o.get("stack"));
            if (o.has("stack")) {
                proto = Stacks.fromJson(reg, o.get("stack"));
            } else {
                var item = Stacks.item(label.contains(":") ? label : "minecraft:" + label);
                proto = item == null ? ItemStack.EMPTY : new ItemStack(item);
            }
            if (proto.isEmpty()) {
                out.add("übersprungen (unbekannt): " + label);
                continue;
            }
            putWare(reg, l, proto, o.has("name") ? o.get("name").getAsString() : null, o.get("preis").getAsDouble(),
                    o.has("einheit") ? o.get("einheit").getAsInt() : 1, o.has("lager") ? o.get("lager").getAsLong() : 64,
                    o.has("soll") ? o.get("soll").getAsLong() : 32);
            ok++;
        }
        out.add(0, ok + " Waren übernommen" + (out.isEmpty() ? "." : ", " + out.size() + " übersprungen:"));
        MarktRuntime.markDirty();
        return out;
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
        boolean r = l.sells.removeIf(o -> o.id.equals(lineId)) | l.buys.removeIf(b -> b.id.equals(lineId))
                | l.waren.removeIf(w -> w.id.equals(lineId));
        if (r) MarktRuntime.markDirty();
        return r;
    }
}
