package de.twogg.markt.server;

import com.google.gson.JsonElement;
import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.Job;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Auftragsbrett. Marktaufträge kommen aus dem Pool der Einstellungen und wechseln regelmäßig; jeder Spieler kann jeden
 * einmal erledigen, solange Plätze frei sind. Spieleraufträge hängen Spieler selbst aus: Die Belohnung wird beim
 * Aushängen eingezogen (Treuhand), die gelieferte Ware landet im Abholfach des Auftraggebers.
 */
public final class BoardService {
    private static final Random RND = new Random();

    private BoardService() {}

    public static void open(ServerPlayer p, BlockPos pos) {
        if (!Send.canOpen(p)) return;
        if (!MarktRuntime.config().jobs.enabled) {
            p.sendOverlayMessage(Component.literal("Am Auftragsbrett hängt gerade nichts.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Pay.settle(p);
        Sessions.open(p, "board", pos, ModContent.BOARD, null);
        send(p, "", false);
    }

    /** Marktaufträge neu ziehen, wenn fällig (oder {@code force}, wenn keine da sind). */
    public static void refreshIfDue(boolean onStart) {
        MarktData d = MarktRuntime.data();
        MarktConfig.Jobs cfg = MarktRuntime.config().jobs;
        long now = MarktRuntime.now();
        boolean none = d.jobs.stream().noneMatch(j -> j.system);
        boolean due = now - d.lastJobRefresh >= cfg.refreshMinutes * MarketMath.MINUTE;
        if (!cfg.enabled || cfg.active <= 0) return;
        if (!due && !(onStart && none)) return;
        reroll();
    }

    public static void reroll() {
        MarktData d = MarktRuntime.data();
        MarktConfig.Jobs cfg = MarktRuntime.config().jobs;
        long now = MarktRuntime.now();
        d.jobs.removeIf(j -> j.system);
        List<MarktConfig.JobTemplate> pool = new ArrayList<>();
        for (MarktConfig.JobTemplate t : cfg.pool) if (Stacks.item(t.item) != null) pool.add(t);
        for (MarktConfig.JobTemplate t : MarketMath.pick(pool, cfg.active, RND)) {
            Job j = new Job();
            j.id = "m" + (d.nextJob++);
            j.system = true;
            j.title = t.title;
            j.item = t.item;
            j.count = t.count;
            j.reward = MarketMath.tenths(t.reward);
            j.slots = cfg.slots;
            j.created = now;
            j.expires = now + cfg.refreshMinutes * MarketMath.MINUTE;
            d.jobs.addFirst(j);
        }
        d.lastJobRefresh = now;
        MarktRuntime.markDirty();
    }

    /** Abgelaufene Spieleraufträge: Belohnung zurück an den Auftraggeber. Erledigte Marktaufträge bleiben bis zum Wechsel. */
    public static void expirePlayerJobs() {
        MarktData d = MarktRuntime.data();
        long now = MarktRuntime.now();
        MinecraftServer server = MarktRuntime.server();
        for (Job j : List.copyOf(d.jobs)) {
            if (j.system || j.expires <= 0 || now < j.expires) continue;
            d.jobs.remove(j);
            if (j.remaining() > 0) refund(server, j, "abgelaufen");
            MarktRuntime.markDirty();
        }
    }

    private static void refund(MinecraftServer server, Job j, String why) {
        long back = j.reward * j.remaining();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(UUID.fromString(j.poster));
        if (p != null) {
            Pay.payout(p, back, false, "markt:auftrag", "Auftrag " + why + ": " + j.title);
            p.sendSystemMessage(Component.literal("Dein Auftrag „" + j.title + "“ ist " + why + ", " + MarktRuntime.fmt(back)
                    + " zurück.").withStyle(ChatFormatting.GOLD));
        } else {
            Pay.later(j.poster, j.posterName, back, "Auftrag " + why + ": " + j.title);
        }
    }

    static void send(ServerPlayer p, String msg, boolean error) {
        MarktConfig.Jobs cfg = MarktRuntime.config().jobs;
        Views.View v = Send.base(p, "board", msg, error);
        v.title = "Auftragsbrett";
        v.subtitle = MarktRuntime.config().title + " · Gelegenheitsjobs";
        long now = MarktRuntime.now();
        String me = p.getUUID().toString();
        var reg = p.level().registryAccess();
        MarktData d = MarktRuntime.data();
        int mine = 0;
        for (Job j : d.jobs) {
            boolean own = !j.system && me.equals(j.poster);
            if (own) mine++;
            if (!j.open(now) && !own) continue;
            Item item = Stacks.item(j.item);
            if (item == null) continue;
            Views.JobLine l = new Views.JobLine();
            l.id = j.id;
            l.title = j.title;
            l.item = Stacks.toString(reg, new ItemStack(item));
            l.itemName = new ItemStack(item).getHoverName().getString();
            l.count = j.count;
            l.reward = j.reward;
            l.remaining = j.remaining();
            l.slots = j.slots;
            l.system = j.system;
            l.mine = own;
            l.done = j.doneBy.contains(me);
            l.poster = j.system ? "Marktleitung" : j.posterName;
            l.expiresIn = j.expires <= 0 ? 0 : j.expires - now;
            l.have = Inv.count(p, Inv.item(item));
            v.jobs.add(l);
        }
        List<JsonElement> pick = d.pickup.get(me);
        if (pick != null) {
            for (JsonElement e : pick) {
                ItemStack s = Stacks.fromJson(reg, e);
                if (s.isEmpty()) continue;
                v.pickupCount += s.getCount();
                if (v.pickup.size() < 6) v.pickup.add(s.getCount() + "× " + s.getHoverName().getString());
            }
        }
        v.playerJobs = cfg.playerJobs;
        v.myJobs = mine;
        v.maxJobs = cfg.maxPerPlayer;
        v.maxReward = MarketMath.tenths(cfg.maxReward);
        v.jobRefreshIn = Math.max(0, d.lastJobRefresh + cfg.refreshMinutes * MarketMath.MINUTE - now);
        ItemStack hand = p.getMainHandItem();
        if (!hand.isEmpty()) {
            v.hand = Stacks.toString(reg, new ItemStack(hand.getItem()));
            v.handName = new ItemStack(hand.getItem()).getHoverName().getString();
        }
        Send.send(p, v);
    }

    public static void handle(ServerPlayer p, Views.Action act) {
        if ("close".equals(act.a)) {
            Sessions.close(p, "board");
            return;
        }
        if (Sessions.check(p, "board") == null) {
            p.sendOverlayMessage(Component.literal("Du stehst nicht mehr am Auftragsbrett.").withStyle(ChatFormatting.RED));
            return;
        }
        if ("refresh".equals(act.a)) {
            if (!Sessions.tooSoon(p)) send(p, "", false);
            return;
        }
        String[] r = switch (act.a) {
            case "deliver" -> deliver(p, act.id);
            case "post" -> post(p, Math.max(1, act.count), act.amount, act.text, act.cash);
            case "cancel" -> cancel(p, act.id);
            case "pickup" -> pickup(p);
            default -> new String[]{"", null};
        };
        send(p, r[0], r[1] != null);
    }

    private static String[] ok(String m) { return new String[]{m, null}; }
    private static String[] fail(String m) { return new String[]{m, "x"}; }

    private static String[] deliver(ServerPlayer p, String id) {
        long now = MarktRuntime.now();
        String me = p.getUUID().toString();
        Job j = MarktRuntime.data().job(id == null ? "" : id);
        if (j == null || !j.open(now)) return fail("Der Auftrag ist schon vergeben oder abgelaufen.");
        if (!j.system && me.equals(j.poster)) return fail("Das ist dein eigener Auftrag.");
        if (j.doneBy.contains(me)) return fail("Den hast du schon erledigt.");
        Item item = Stacks.item(j.item);
        if (item == null) return fail("Dieses Item gibt es nicht.");
        if (Inv.count(p, Inv.item(item)) < j.count) return fail("Du brauchst " + j.count + "× " + new ItemStack(item).getHoverName().getString() + ".");
        List<ItemStack> taken = Inv.take(p, Inv.item(item), j.count);
        if (!j.system) {
            var reg = p.level().registryAccess();
            List<JsonElement> box = MarktRuntime.data().pickup.computeIfAbsent(j.poster, k -> new ArrayList<>());
            for (ItemStack s : taken) {
                JsonElement e = Stacks.toJson(reg, s);
                if (e != null) box.add(e);
            }
            ServerPlayer poster = p.level().getServer().getPlayerList().getPlayer(UUID.fromString(j.poster));
            if (poster != null) {
                poster.sendSystemMessage(Component.literal(p.getGameProfile().name() + " hat deinen Auftrag „" + j.title
                        + "“ erledigt. Die Ware liegt im Abholfach am Auftragsbrett.").withStyle(ChatFormatting.GREEN));
            }
        }
        j.doneBy.add(me);
        if (!j.system && j.remaining() <= 0) MarktRuntime.data().jobs.remove(j);
        MarktRuntime.markDirty();
        Pay.payout(p, j.reward, false, "markt:auftrag", "Auftrag: " + j.title);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        TwoggMarkt.LOGGER.info("{} erledigt Auftrag {} ({})", p.getGameProfile().name(), j.id, j.title);
        return ok("Erledigt: " + j.title + " – " + MarktRuntime.fmt(j.reward) + " aufs Konto.");
    }

    private static String[] post(ServerPlayer p, int count, long reward, String title, boolean cash) {
        MarktConfig.Jobs cfg = MarktRuntime.config().jobs;
        String me = p.getUUID().toString();
        if (!cfg.playerJobs) return fail("Spieleraufträge sind abgeschaltet.");
        long mine = MarktRuntime.data().jobs.stream().filter(j -> !j.system && me.equals(j.poster)).count();
        if (cfg.maxPerPlayer > 0 && mine >= cfg.maxPerPlayer) return fail("Du hast schon " + mine + " Aufträge ausgehängt.");
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) return fail("Nimm ein Stück der gewünschten Ware in die Hand.");
        if (count > 64 * 9) return fail("Höchstens " + (64 * 9) + " Stück.");
        if (reward <= 0 || reward > MarketMath.tenths(cfg.maxReward)) {
            return fail("Belohnung zwischen 0,1 und " + MarketMath.coins(MarketMath.tenths(cfg.maxReward)) + " eingeben.");
        }
        String itemName = hand.getHoverName().getString();
        String t = title == null || title.isBlank() ? "Suche " + itemName : title.trim();
        if (t.length() > 48) t = t.substring(0, 48);
        TxResult tx = Pay.charge(p, reward, cash, "markt:auftrag", "Auftrag ausgehängt: " + t);
        if (!tx.ok()) return fail(tx.message());
        Job j = new Job();
        j.id = "s" + (MarktRuntime.data().nextJob++);
        j.system = false;
        j.title = t;
        j.item = Stacks.id(hand);
        j.count = count;
        j.reward = reward;
        j.slots = 1;
        j.created = MarktRuntime.now();
        j.expires = j.created + cfg.playerJobHours * 60 * MarketMath.MINUTE;
        j.poster = me;
        j.posterName = p.getGameProfile().name();
        MarktRuntime.data().jobs.add(j);
        MarktRuntime.markDirty();
        return ok("Ausgehängt: " + count + "× " + itemName + " für " + MarktRuntime.fmt(reward) + ". Die Belohnung ist hinterlegt.");
    }

    private static String[] cancel(ServerPlayer p, String id) {
        Job j = MarktRuntime.data().job(id == null ? "" : id);
        if (j == null || j.system || !p.getUUID().toString().equals(j.poster)) return fail("Das ist nicht dein Auftrag.");
        MarktRuntime.data().jobs.remove(j);
        MarktRuntime.markDirty();
        long back = j.reward * j.remaining();
        if (back > 0) Pay.payout(p, back, false, "markt:auftrag", "Auftrag zurückgezogen: " + j.title);
        return ok("Auftrag zurückgezogen" + (back > 0 ? ", " + MarktRuntime.fmt(back) + " zurück." : "."));
    }

    private static String[] pickup(ServerPlayer p) {
        List<JsonElement> box = MarktRuntime.data().pickup.remove(p.getUUID().toString());
        if (box == null || box.isEmpty()) return fail("Dein Abholfach ist leer.");
        var reg = p.level().registryAccess();
        int n = 0;
        for (JsonElement e : box) {
            ItemStack s = Stacks.fromJson(reg, e);
            if (s.isEmpty()) continue;
            n += s.getCount();
            Inv.give(p, s);
        }
        MarktRuntime.markDirty();
        return ok(n + " Stück abgeholt.");
    }
}
