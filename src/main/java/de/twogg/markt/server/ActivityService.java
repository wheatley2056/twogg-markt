package de.twogg.markt.server;

import com.google.gson.JsonElement;
import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktEvent;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Mitmach-Aktionen des Weihnachtsmarkts: Losbude (Lose und Wichteln während des Events, Ziehung und Verteilung im
 * Finale), Adventskalender (1.–24. Dezember ein Türchen pro Tag) und versteckte Geschenke.
 */
public final class ActivityService {
    private ActivityService() {}

    // ------------------------------------------------------------------ Öffnen

    public static void openLottery(ServerPlayer p, BlockPos pos) {
        if (!Send.canOpen(p)) return;
        if (!MarktRuntime.config().event.lottery && !MarktRuntime.config().event.wichteln) {
            p.sendOverlayMessage(Component.literal("Die Losbude ist zu.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Pay.settle(p);
        Sessions.open(p, "lottery", pos, ModContent.LOTTERY, null);
        send(p, "lottery", "", false);
    }

    public static void openAdvent(ServerPlayer p, BlockPos pos) {
        if (!Send.canOpen(p)) return;
        if (!MarktRuntime.config().event.advent) {
            p.sendOverlayMessage(Component.literal("Der Adventskalender ist heute geschlossen.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Pay.settle(p);
        Sessions.open(p, "advent", pos, ModContent.ADVENT, null);
        send(p, "advent", "", false);
    }

    /** Verstecktes Geschenk: einmal pro Spieler und Event. */
    public static void openGift(ServerPlayer p, BlockPos pos) {
        MarktEvent e = EventService.ev();
        if (!e.running()) {
            p.sendOverlayMessage(Component.literal("Ein verschnürtes Geschenk … die Suche läuft nur, wenn der Weihnachtsmarkt geöffnet ist.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        String key = p.getUUID() + "@" + p.level().dimension().identifier() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        if (e.giftsFound.contains(key)) {
            p.sendOverlayMessage(Component.literal("Dieses Geschenk hast du schon gefunden.").withStyle(ChatFormatting.GRAY));
            return;
        }
        e.giftsFound.add(key);
        EventService.recordGift(p);
        MarktRuntime.markDirty();
        long reward = MarketMath.tenths(MarktRuntime.config().event.giftReward);
        if (reward > 0) Pay.payout(p, reward, false, "markt:geschenk", "Geschenk gefunden");
        long found = e.stats(p.getUUID().toString(), p.getGameProfile().name()).gifts;
        p.sendOverlayMessage(Component.literal("🎁 Geschenk gefunden! " + (reward > 0 ? "+" + MarktRuntime.fmt(reward) + " · " : "")
                + found + (found == 1 ? " Geschenk" : " Geschenke") + " in diesem Event").withStyle(ChatFormatting.GOLD));
        ServerLevel level = (ServerLevel) p.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.02);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.8f);
    }

    // ------------------------------------------------------------------ Ansicht

    static void send(ServerPlayer p, String screen, String msg, boolean error) {
        Views.View v = Send.base(p, screen, msg, error);
        v.event = EventService.info(p, false);
        switch (screen) {
            case "lottery" -> {
                v.title = "Losbude";
                v.subtitle = MarktRuntime.config().title + " · Lose und Wichteln, Auflösung bei der Siegerehrung";
                ItemStack hand = p.getMainHandItem();
                if (!hand.isEmpty()) {
                    v.hand = Stacks.toString(p.level().registryAccess(), hand);
                    v.handName = hand.getCount() + "× " + hand.getHoverName().getString();
                }
            }
            case "advent" -> {
                v.title = "Adventskalender";
                v.subtitle = MarktRuntime.config().title + " · jeden Tag ein Türchen";
                fillAdvent(p, v);
            }
            default -> { }
        }
        Send.send(p, v);
    }

    private static void fillAdvent(ServerPlayer p, Views.View v) {
        LocalDate today = LocalDate.now();
        int day = today.getMonthValue() == 12 && today.getDayOfMonth() <= 24 ? today.getDayOfMonth() : 0;
        v.adventToday = day;
        List<Integer> opened = adventOpened(p.getUUID().toString());
        var reg = p.level().registryAccess();
        for (int d = 1; d <= 24; d++) {
            Views.AdventDoor door = new Views.AdventDoor();
            door.day = d;
            door.state = opened.contains(d) ? "DONE" : d == day ? "OPEN" : day > 0 && d < day ? "MISSED" : "LOCKED";
            if (opened.contains(d)) {
                MarktConfig.AdventDay a = MarktRuntime.config().advent(d);
                door.text = a == null ? "" : reward(a);
                Item it = a == null ? null : Stacks.item(a.item);
                if (it != null) door.item = Stacks.toString(reg, new ItemStack(it));
            }
            v.advent.add(door);
        }
    }

    private static List<Integer> adventOpened(String uuid) {
        var d = MarktRuntime.data();
        int year = LocalDate.now().getYear();
        if (d.adventYear != year) {
            d.adventYear = year;
            d.advent.clear();
            MarktRuntime.markDirty();
        }
        return d.advent.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    private static String reward(MarktConfig.AdventDay a) {
        List<String> parts = new ArrayList<>();
        if (a.coins > 0) parts.add(MarktRuntime.fmt(MarketMath.tenths(a.coins)));
        Item it = Stacks.item(a.item);
        if (it != null && a.count > 0) parts.add(a.count + "× " + new ItemStack(it).getHoverName().getString());
        String r = String.join(" + ", parts);
        return a.text.isBlank() ? r : a.text + " " + r;
    }

    // ------------------------------------------------------------------ Aktionen

    public static void handle(ServerPlayer p, Views.Action act) {
        String screen = act.screen;
        if ("close".equals(act.a)) {
            Sessions.close(p, screen, act.session);
            return;
        }
        if (Sessions.check(p, screen) == null) {
            p.sendOverlayMessage(Component.literal("Du stehst nicht mehr davor.").withStyle(ChatFormatting.RED));
            return;
        }
        if ("refresh".equals(act.a)) {
            if (!Sessions.tooSoon(p)) send(p, screen, "", false);
            return;
        }
        String[] r = switch (screen + ":" + act.a) {
            case "lottery:buy" -> MarktRuntime.config().event.lottery ? buyTickets(p, Math.max(1, act.count), act.cash) : fail("Lose gibt es gerade nicht.");
            case "advent:open" -> openDoor(p);
            case "lottery:give" -> MarktRuntime.config().event.wichteln ? giveWichtel(p) : fail("Wichteln ist abgeschaltet.");
            case "lottery:take" -> takeWichtel(p);
            case "lottery:pickup" -> pickup(p);
            default -> new String[]{"", null};
        };
        send(p, screen, r[0], r[1] != null);
    }

    private static String[] ok(String m) { return new String[]{m, null}; }
    private static String[] fail(String m) { return new String[]{m, "x"}; }

    private static String[] buyTickets(ServerPlayer p, int count, boolean cash) {
        MarktEvent e = EventService.ev();
        MarktConfig.Event c = MarktRuntime.config().event;
        if (!e.running()) return fail("Lose gibt es nur, solange der Weihnachtsmarkt geöffnet ist.");
        String me = p.getUUID().toString();
        int have = e.tickets.getOrDefault(me, 0);
        count = Math.min(count, c.maxTickets - have);
        if (count <= 0) return fail("Mehr als " + c.maxTickets + " Lose pro Spieler gehen nicht.");
        long price = EventService.ticketPrice() * count;
        TxResult tx = Pay.charge(p, price, cash, "markt:lose", count + (count == 1 ? " Los" : " Lose"));
        if (!tx.ok()) return fail(tx.message());
        e.tickets.put(me, have + count);
        e.stats(me, p.getGameProfile().name());
        e.pot += price;
        MarktRuntime.markDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.2f);
        return ok(count + (count == 1 ? " Los" : " Lose") + " gekauft (" + MarktRuntime.fmt(price) + "). Du hast jetzt " + (have + count) + ". Viel Glück!");
    }

    private static String[] openDoor(ServerPlayer p) {
        LocalDate today = LocalDate.now();
        if (today.getMonthValue() != 12 || today.getDayOfMonth() > 24) return fail("Der Adventskalender läuft vom 1. bis 24. Dezember.");
        int day = today.getDayOfMonth();
        List<Integer> opened = adventOpened(p.getUUID().toString());
        if (opened.contains(day)) return fail("Das Türchen für heute hast du schon geöffnet. Morgen wieder!");
        MarktConfig.AdventDay a = MarktRuntime.config().advent(day);
        opened.add(day);
        MarktRuntime.markDirty();
        if (a == null) return ok("Türchen " + day + ": leer – aber es duftet nach Zimt.");
        long coins = MarketMath.tenths(a.coins);
        if (coins > 0) Pay.payout(p, coins, false, "markt:advent", "Adventskalender " + day + ". Dezember");
        Item it = Stacks.item(a.item);
        if (it != null && a.count > 0) Inv.give(p, new ItemStack(it, a.count));
        p.level().playSound(null, p.blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
        return ok("Türchen " + day + ": " + reward(a));
    }

    private static String[] giveWichtel(ServerPlayer p) {
        MarktEvent e = EventService.ev();
        if (!e.running()) return fail("Wichtelgeschenke nimmt die Losbude nur während des Weihnachtsmarkts an.");
        String me = p.getUUID().toString();
        for (MarktEvent.Gift g : e.wichtel) if (g.from.equals(me)) return fail("Du hast schon ein Wichtelgeschenk abgegeben.");
        ItemStack hand = p.getMainHandItem();
        if (hand.isEmpty()) return fail("Nimm dein Geschenk in die Hand.");
        if (hand.is(ModContent.PLANNER)) return fail("Den Marktplaner verschenkst du lieber nicht.");
        MarktEvent.Gift g = new MarktEvent.Gift();
        g.from = me;
        g.name = p.getGameProfile().name();
        g.item = Stacks.toJson(p.level().registryAccess(), hand.copy());
        if (g.item == null) return fail("Dieses Item lässt sich nicht einpacken.");
        String what = hand.getCount() + "× " + hand.getHoverName().getString();
        p.getInventory().setItem(p.getInventory().getSelectedSlot(), ItemStack.EMPTY);
        e.wichtel.add(g);
        MarktRuntime.markDirty();
        p.level().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
        return ok(what + " ist abgegeben. Bei der Siegerehrung bekommt jemand anderes es – und du ein anderes Geschenk.");
    }

    private static String[] takeWichtel(ServerPlayer p) {
        MarktEvent e = EventService.ev();
        String me = p.getUUID().toString();
        for (MarktEvent.Gift g : List.copyOf(e.wichtel)) {
            if (!g.from.equals(me)) continue;
            e.wichtel.remove(g);
            Inv.give(p, Stacks.fromJson(p.level().registryAccess(), g.item));
            MarktRuntime.markDirty();
            return ok("Geschenk zurückgenommen.");
        }
        return fail("Von dir liegt kein Wichtelgeschenk bereit.");
    }

    private static String[] pickup(ServerPlayer p) {
        List<JsonElement> box = MarktRuntime.data().pickup.remove(p.getUUID().toString());
        if (box == null || box.isEmpty()) return fail("Für dich liegt nichts bereit.");
        int n = 0;
        for (JsonElement el : box) {
            ItemStack s = Stacks.fromJson(p.level().registryAccess(), el);
            if (s.isEmpty()) continue;
            n += s.getCount();
            Inv.give(p, s);
        }
        MarktRuntime.markDirty();
        return ok(n + " Stück abgeholt. Frohe Weihnachten!");
    }
}
