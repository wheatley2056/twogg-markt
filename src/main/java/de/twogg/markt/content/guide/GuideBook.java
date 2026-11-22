package de.twogg.markt.content.guide;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.net.Views;
import de.twogg.markt.server.Send;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * Das Markt-Handbuch (seit 0.3.0): eigenes Item; Rechtsklick öffnet im Client ein eigenes Buch mit Doppelseiten, Bildern
 * aus dem Spiel, Lesezeichen und anklickbarem Inhaltsverzeichnis ({@code client/HandbookScreen}). Text und Bilder liegen
 * im Mod-Jar ({@code assets/twogg_markt/handbuch/de_de.txt}, {@code textures/gui/handbuch/}); die Zahlen darin
 * (Gebühr, Preise, Grenzen) schickt der Server aus der aktuellen {@code markt.json} mit.
 */
public final class GuideBook {

    public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, TwoggMarkt.id("handbuch"));
    public static Item ITEM;

    private GuideBook() {
    }

    public static void init() {
        ITEM = Registry.register(BuiltInRegistries.ITEM, KEY,
                new HandbookItem(new Item.Properties().setId(KEY).stacksTo(1).rarity(Rarity.UNCOMMON)));
    }

    /** Rechtsklick: Buch beim Client öffnen. */
    static final class HandbookItem extends Item {
        HandbookItem(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide() && player instanceof ServerPlayer sp) {
                open(sp);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /** Buch beim Client öffnen (mit den Werten aus der Config). */
    public static void open(ServerPlayer p) {
        if (!Send.canOpen(p)) {
            return;
        }
        Views.View v = Send.base(p, "handbuch", "", false);
        v.guide = values(MarktRuntime.config());
        Send.send(p, v);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1f);
    }

    /** Werte für die Platzhalter im Text. */
    public static Map<String, String> values(MarktConfig c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("title", c.title);
        v.put("subtitle", c.subtitle);
        v.put("period", c.stalls.periodName);
        v.put("maxStalls", c.stalls.maxPerPlayer == 1 ? "einen Stand" : c.stalls.maxPerPlayer + " Stände");
        v.put("maxPerPlayer", String.valueOf(c.stalls.maxPerPlayer));
        v.put("maxHelpers", String.valueOf(c.stalls.maxHelpers));
        v.put("counterPrice", num(c.shop.counterPrice));
        v.put("rentRefund", num(c.stalls.rentRefundPercent));
        v.put("buyback", num(c.stalls.buybackPercent));
        v.put("maxOffers", String.valueOf(c.shop.maxOffers));
        v.put("fee", num(c.shop.feePercent));
        v.put("minFactor", num(c.buyer.minFactor * 100));
        v.put("jobsActive", String.valueOf(c.jobs.active));
        v.put("jobsMax", String.valueOf(c.jobs.maxPerPlayer));
        v.put("jobsMaxReward", num(c.jobs.maxReward));
        v.put("snowBlocks", String.valueOf(c.snow.blocksPerPay));
        v.put("snowPay", num(c.snow.pay));
        v.put("snowDaily", num(c.snow.dailyMax));
        v.put("ticket", num(c.event.ticketPrice));
        v.put("maxTickets", String.valueOf(c.event.maxTickets));
        v.put("pot", num(c.event.potPayoutPercent));
        v.put("giftReward", num(c.event.giftReward));
        int n = c.event.lotteryShares.size();
        v.put("winners", n == 1 ? "einen Gewinner" : n == 2 ? "zwei Gewinner" : n == 3 ? "drei Gewinner" : n + " Gewinner");
        v.put("shares", String.join(" / ", c.event.lotteryShares.stream().map(GuideBook::num).toList()));
        return v;
    }

    /** Platzhalter {@code {name}} ersetzen; unbekannte bleiben stehen. */
    public static String fill(String text, Map<String, String> values) {
        String out = text;
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return out;
    }

    /** Zahl deutsch: 0,1 · 50 · 2,5. */
    static String num(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return String.valueOf((long) Math.rint(d));
        }
        return String.format(Locale.GERMANY, "%.2f", d).replaceAll("0+$", "").replaceAll(",$", "");
    }

    public static ItemStack create() {
        return new ItemStack(ITEM);
    }

    public static boolean is(ItemStack stack) {
        return stack.is(ITEM);
    }

    /** Ins Inventar legen (was nicht passt, fällt vor die Füße). */
    public static void give(ServerPlayer p) {
        ItemStack book = create();
        if (!p.getInventory().add(book)) {
            p.drop(book, false);
        }
    }
}
