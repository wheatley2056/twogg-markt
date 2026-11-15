package de.twogg.markt.test;

import com.mojang.authlib.GameProfile;
import de.twogg.economy.api.Economy;
import de.twogg.economy.core.Money;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.client.AdminScreen;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.Job;
import de.twogg.markt.core.Plot;
import de.twogg.markt.net.Views;
import de.twogg.markt.server.BoardService;
import de.twogg.markt.server.BuyerService;
import de.twogg.markt.server.Selections;
import de.twogg.markt.server.Sessions;
import de.twogg.markt.server.ShopService;
import de.twogg.markt.server.StallService;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.List;
import java.util.UUID;

/**
 * Durchgang im echten Spiel: Stand einzeichnen, mieten, Theke mit Angebot, Kauf durch einen zweiten (Fake-)Spieler,
 * Kasse leeren, Ankauf, Auftragsbrett (liefern und aushängen), Räumdienst, Bauschutz, Ablauf der Miete und alle
 * Seiten der Marktverwaltung – mit Screenshots und Prüfungen auf dem Server.
 */
public final class MarktClientTest implements FabricClientGameTest {
    private static final String DIM = "minecraft:overworld";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(ui -> ui.setAllowCommands(true)).create()) {
            sp.getClientLevel().waitForChunksRender();
            sp.getServer().runCommand("time set noon");
            sp.getServer().runCommand("weather clear");
            sp.getServer().runCommand("gamerule show_advancement_messages false");
            sp.getServer().runCommand("gamemode survival @a");
            BlockPos base = ctx.computeOnClient(mc -> mc.player.blockPosition());
            // Ebene Fläche bauen: Boden aus Fichtenbrettern, darüber Luft
            sp.getServer().runOnServer(server -> {
                ServerLevel l = server.overworld();
                for (int x = -12; x <= 12; x++)
                    for (int z = -4; z <= 16; z++) {
                        l.setBlockAndUpdate(base.offset(x, -1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                        for (int y = 0; y < 6; y++) l.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
            });
            sp.getServer().runCommand("tp @p " + (base.getX() + 0.5) + " " + base.getY() + " " + (base.getZ() + 0.5) + " 0 25");

            // 1) Stand einzeichnen (wie mit dem Marktplaner: zwei Ecken) und anlegen
            BlockPos a = base.offset(-2, 0, 3), b = base.offset(2, 4, 7);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = player(server);
                Selections.corner(p, a, 1);
                Selections.corner(p, b, 2);
            });
            playerCommand(ctx, "markt stand neu Glühweinbude");
            Plot plot = sp.getServer().computeOnServer(server -> MarktRuntime.data().plots.get("gluehweinbude"));
            check(plot != null, "Stand wurde nicht angelegt");
            // Zweiter Stand (bleibt frei) und Schild davor
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = player(server);
                Selections.corner(p, base.offset(4, 0, 3), 1);
                Selections.corner(p, base.offset(8, 4, 7), 2);
            });
            playerCommand(ctx, "markt stand neu Lebkuchenstand");
            playerCommand(ctx, "markt stand beschreibung lebkuchenstand Direkt am Eingang, mit Blick auf die Eisbahn");
            BlockPos sign = base.offset(0, 0, 2);
            sp.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(sign,
                    ModContent.STALL_SIGN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)));
            ctx.waitTicks(20);
            sp.getClientLevel().waitForChunksRender();
            ctx.takeScreenshot("markt-01-schild");

            // 2) Standschild per Rechtsklick → Marktfenster mit dem Stand vorgewählt
            ctx.getInput().pressKey(options -> options.keyUse);
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("MarketScreen"), 100);
            ctx.waitTicks(10);
            ctx.takeScreenshot("markt-02-staende");
            long bal0 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            sp.getServer().runOnServer(server -> Economy.pay(player(server), Money.ofCoins(50), "test", "Startkapital"));
            ctx.waitTicks(2);
            press(ctx, "Mieten", true);
            ctx.waitTicks(15);
            ctx.takeScreenshot("markt-03-gemietet");
            boolean rented = sp.getServer().computeOnServer(server -> {
                Plot p = MarktRuntime.data().plots.get("gluehweinbude");
                return p.isOwner(player(server).getUUID().toString(), MarktRuntime.now()) && p.status(MarktRuntime.now()) == Plot.Status.RENTED;
            });
            check(rented, "Mieten hat nicht geklappt");
            long bal1 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            check(bal1 == bal0 + 500 - 15, "Miete 1,5 WLC erwartet: " + bal0 + " → " + bal1);
            int counters = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(ModContent.COUNTER_ITEM));
            check(counters == 1, "Verkaufstheke nicht ins Inventar gelegt: " + counters);
            press(ctx, "Mein Stand", false);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-04-mein-stand");
            press(ctx, "Verdienen", false);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-05-verdienen");
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 3) Theke und Lagerkiste in den Stand, Angebot aus der Hand anlegen
            BlockPos counter = base.offset(0, 0, 4), chest = base.offset(1, 0, 6);
            sp.getServer().runOnServer(server -> {
                ServerLevel l = server.overworld();
                l.setBlockAndUpdate(counter, ModContent.COUNTER.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                l.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
                if (l.getBlockEntity(chest) instanceof ChestBlockEntity c) c.setItem(0, new ItemStack(Items.COOKIE, 20));
                ServerPlayer p = player(server);
                p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COOKIE, 1));
                ShopService.open(p, counter);
            });
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("ShopScreen"), 100);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-06-theke-leer");
            type(ctx, "Preis", "2");
            type(ctx, "Stück", "4");
            press(ctx, "Anbieten", false);
            ctx.waitTicks(10);
            ctx.takeScreenshot("markt-07-theke-angebot");
            int offers = sp.getServer().computeOnServer(server -> MarktRuntime.data().plots.get("gluehweinbude").offers.size());
            check(offers == 1, "Angebot nicht angelegt: " + offers);

            // 4) Ein zweiter Spieler kauft zweimal (FakePlayer, kein Admin)
            String buyResult = sp.getServer().computeOnServer(server -> {
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(UUID.nameUUIDFromBytes("kunde".getBytes()), "Kunde"));
                f.setPos(counter.getX() + 0.5, counter.getY(), counter.getZ() - 1.5);
                Economy.pay(f, Money.ofCoins(20), "test", "Taschengeld");
                Plot p = MarktRuntime.data().plots.get("gluehweinbude");
                Sessions.open(f, "shop", counter, ModContent.COUNTER, p.id);
                Views.Action act = Views.Action.of("buy");
                act.screen = "shop";
                act.id = p.offers.getFirst().id;
                act.count = 2;
                ShopService.handle(f, act);
                int got = f.getInventory().countItem(Items.COOKIE);
                int left = ((ChestBlockEntity) server.overworld().getBlockEntity(chest)).countItem(Items.COOKIE);
                return got + "/" + left + "/" + p.till + "/" + Economy.balance(f);
            });
            // 2 Käufe × 4 Kekse = 8; 2 × 2 WLC = 4 WLC, 5 % Gebühr = 0,2 → 3,8 in der Kasse; Kunde 5 + 20 − 4 = 21
            check(buyResult.equals("8/12/38/210"), "Kauf am Stand: Kekse/Lager/Kasse/Konto = " + buyResult);
            sp.getServer().runOnServer(server -> ShopService.open(player(server), counter));
            ctx.waitTicks(10);
            press(ctx, "Verwalten", false);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-08-theke-verwalten");
            long bal2 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            press(ctx, "Kasse leeren", false);
            ctx.waitTicks(10);
            long bal3 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            check(bal3 == bal2 + 38, "Kasse leeren: " + bal2 + " → " + bal3);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 5) Bauschutz: der Kunde darf im Stand nichts abbauen, außerhalb schon
            String prot = sp.getServer().computeOnServer(server -> {
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(UUID.nameUUIDFromBytes("kunde".getBytes()), "Kunde"));
                f.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                ServerLevel l = server.overworld();
                BlockPos inside = base.offset(0, 0, 5), outside = base.offset(0, 0, -3);
                l.setBlockAndUpdate(inside, Blocks.OAK_PLANKS.defaultBlockState());
                l.setBlockAndUpdate(outside, Blocks.OAK_PLANKS.defaultBlockState());
                f.gameMode.destroyBlock(inside);
                f.gameMode.destroyBlock(outside);
                return l.getBlockState(inside).is(Blocks.OAK_PLANKS) + "/" + l.getBlockState(outside).isAir();
            });
            check(prot.equals("true/true"), "Bauschutz (drinnen bleibt / draußen weg): " + prot);

            // 6) Ankauf: 16 Fichtenstämme verkaufen
            BlockPos buyer = base.offset(-4, 0, 0);
            sp.getServer().runOnServer(server -> {
                server.overworld().setBlockAndUpdate(buyer, ModContent.BUYER.defaultBlockState());
                ServerPlayer p = player(server);
                p.getInventory().add(new ItemStack(Items.SPRUCE_LOG, 16));
                BuyerService.open(p, buyer);
            });
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("BuyerScreen"), 100);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-09-ankauf");
            long bal4 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            press(ctx, "Alle", false);   // erste Zeile: Fichtenholz, 8 Stück für 0,2
            ctx.waitTicks(10);
            ctx.takeScreenshot("markt-10-ankauf-verkauft");
            String sold = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.SPRUCE_LOG)
                    + "/" + (Economy.balance(player(server)) - bal4));
            check(sold.equals("0/4"), "Ankauf 2 × 8 Fichtenstämme = 0,4 WLC: " + sold);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 7) Auftragsbrett: Marktauftrag liefern, eigenen Auftrag aushängen
            BlockPos board = base.offset(-6, 0, 0);
            String job = sp.getServer().computeOnServer(server -> {
                server.overworld().setBlockAndUpdate(board, ModContent.BOARD.defaultBlockState());
                Job j = MarktRuntime.data().jobs.stream().filter(x -> x.system).findFirst().orElse(null);
                if (j == null) return "";
                ServerPlayer p = player(server);
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(j.item));
                p.getInventory().add(new ItemStack(item, j.count));
                BoardService.open(p, board);
                return j.id + ":" + j.reward;
            });
            check(!job.isEmpty(), "Keine Marktaufträge am Brett");
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("BoardScreen"), 100);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-11-auftragsbrett");
            long bal5 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            press(ctx, "Liefern", false);
            ctx.waitTicks(10);
            long reward = Long.parseLong(job.split(":")[1]);
            long bal6 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            check(bal6 == bal5 + reward, "Auftrag liefern: " + bal5 + " → " + bal6 + " (Belohnung " + reward + ")");
            sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LANTERN)));
            press(ctx, "Aushängen", false);
            ctx.waitTicks(5);
            type(ctx, "Stück", "4");
            type(ctx, "Belohnung", "3");
            type(ctx, "Titel (optional)", "Laternen für meine Bude");
            ctx.waitTicks(2);
            ctx.takeScreenshot("markt-12-aushaengen");
            press(ctx, "Auftrag aushängen", false);
            ctx.waitTicks(10);
            String posted = sp.getServer().computeOnServer(server -> {
                Job j = MarktRuntime.data().jobs.stream().filter(x -> !x.system).findFirst().orElse(null);
                return j == null ? "" : j.title + "/" + j.count + "/" + j.reward + "/" + (bal6 - Economy.balance(player(server)));
            });
            check(posted.equals("Laternen für meine Bude/4/30/30"), "Spielerauftrag: " + posted);
            press(ctx, "Aufträge", false);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-13-auftraege");
            // Kunde liefert die Laternen; die Ware landet im Abholfach
            String delivered = sp.getServer().computeOnServer(server -> {
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(UUID.nameUUIDFromBytes("kunde".getBytes()), "Kunde"));
                f.setPos(board.getX() + 0.5, board.getY(), board.getZ() - 1.5);
                f.getInventory().add(new ItemStack(Items.LANTERN, 4));
                long before = Economy.balance(f);
                Job j = MarktRuntime.data().jobs.stream().filter(x -> !x.system).findFirst().orElseThrow();
                Sessions.open(f, "board", board, ModContent.BOARD, null);
                Views.Action act = Views.Action.of("deliver");
                act.screen = "board";
                act.id = j.id;
                BoardService.handle(f, act);
                var box = MarktRuntime.data().pickup.get(player(server).getUUID().toString());
                return (Economy.balance(f) - before) + "/" + (box == null ? 0 : box.size());
            });
            check(delivered.equals("30/1"), "Spielerauftrag geliefert (Belohnung/Abholfach): " + delivered);
            sp.getServer().runOnServer(server -> BoardService.open(player(server), board));
            ctx.waitTicks(10);
            press(ctx, "Abholfach", false);
            ctx.waitTicks(5);
            ctx.takeScreenshot("markt-14-abholfach");
            press(ctx, "Alles abholen", false);
            ctx.waitTicks(10);
            int lanterns = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.LANTERN));
            check(lanterns >= 4, "Abholen: Laternen " + lanterns);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 8) Räumdienst: Zone, 16 Schneeschichten räumen = 2 × 0,1
            String snow = sp.getServer().computeOnServer(server -> {
                ServerPlayer p = player(server);
                Selections.corner(p, base.offset(-12, 0, 10), 1);
                Selections.corner(p, base.offset(12, 2, 16), 2);
                return "";
            });
            playerCommand(ctx, "markt zone neu Hauptgasse");
            String snowPay = sp.getServer().computeOnServer(server -> {
                ServerPlayer p = player(server);
                ServerLevel l = server.overworld();
                long before = Economy.balance(p);
                for (int i = 0; i < 16; i++) {
                    BlockPos s = base.offset(-8 + i, 0, 12);
                    l.setBlockAndUpdate(s, Blocks.SNOW.defaultBlockState());
                    p.gameMode.destroyBlock(s);
                }
                return MarktRuntime.data().zones.size() + "/" + (Economy.balance(p) - before);
            });
            check(snowPay.equals("1/2") , "Räumdienst (Zonen/Lohn): " + snowPay + snow);

            // 9) Ablauf: Miete endet, Stand wird frei, Kasse bleibt erhalten (vorgemerkt bzw. ausgezahlt)
            String expired = sp.getServer().computeOnServer(server -> {
                Plot p = MarktRuntime.data().plots.get("gluehweinbude");
                p.till = 7;
                p.rentedUntil = MarktRuntime.now() - 1;
                long before = Economy.balance(player(server));
                StallService.checkExpiry();
                return p.status(MarktRuntime.now()) + "/" + p.owner.isEmpty() + "/" + (Economy.balance(player(server)) - before);
            });
            check(expired.equals("FREE/true/7"), "Ablauf (Status/Besitzer leer/Kasse ausgezahlt): " + expired);

            // 9b) Käufer-Ansicht: Der Stand gehört jetzt dem Kunden, der Spieler kauft bar an der Theke
            sp.getServer().runOnServer(server -> {
                Plot p = MarktRuntime.data().plots.get("gluehweinbude");
                p.owner = UUID.nameUUIDFromBytes("kunde".getBytes()).toString();
                p.ownerName = "Kunde";
                p.rentedUntil = MarktRuntime.now() + 3_600_000;
                var o = new de.twogg.markt.core.Offer();
                o.id = "o1";
                o.item = de.twogg.markt.data.Stacks.toJson(server.registryAccess(), new ItemStack(Items.COOKIE));
                o.name = "Lebkuchen";
                o.count = 3;
                o.price = 15;
                p.offers.add(o);
                ServerPlayer pl = player(server);
                pl.getInventory().clearContent();
            });
            sp.getServer().runCommand("eco cash Player0 10");
            sp.getServer().runOnServer(server -> ShopService.open(player(server), counter));
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("ShopScreen"), 100);
            ctx.waitTicks(5);
            press(ctx, "Konto", false);   // auf Bar umschalten
            ctx.waitTicks(2);
            press(ctx, "Mehr", false);    // Menge 2
            ctx.waitTicks(2);
            ctx.takeScreenshot("markt-14b-theke-kaeufer");
            press(ctx, "Kaufen", true);
            ctx.waitTicks(10);
            ctx.takeScreenshot("markt-14c-theke-gekauft");
            String cashBuy = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.COOKIE)
                    + "/" + Economy.cash(player(server)) + "/" + MarktRuntime.data().plots.get("gluehweinbude").till);
            // 2 × 3 Kekse für 2 × 1,5 = 3 WLC bar: 10 − 3 = 7 WLC Bargeld; Kasse 3 − 0,2 Gebühr = 2,8
            check(cashBuy.equals("6/70/28"), "Barkauf an der Theke (Kekse/Bargeld/Kasse): " + cashBuy);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 10) Marktverwaltung: alle Seiten
            ctx.runOnClient(mc -> mc.player.connection.sendCommand("markt admin"));
            ctx.waitFor(mc -> mc.screen instanceof AdminScreen, 100);
            ctx.waitTicks(10);
            ctx.takeScreenshot("markt-15-admin-staende");
            for (String page : new String[]{"Neu", "Ankauf", "Aufträge", "Räumzonen", "Regeln"}) {
                press(ctx, page, false);
                ctx.waitTicks(5);
                ctx.takeScreenshot("markt-16-admin-" + page.toLowerCase().replace("ä", "ae"));
            }
            // Regel ändern: Marktgebühr über das Eingabefeld
            press(ctx, "Stände", false);
            ctx.waitTicks(5);
            press(ctx, "Art: Miete + Kauf", false);
            ctx.waitTicks(10);
            String mode = sp.getServer().computeOnServer(server -> MarktRuntime.data().plots.get("gluehweinbude").mode.name());
            check(mode.equals("RENT"), "Art umschalten: " + mode);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 11) Planer in der Hand: Umrisse sichtbar
            sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.PLANNER)));
            sp.getServer().runCommand("tp @p " + (base.getX() + 0.5) + " " + (base.getY() + 3) + " " + (base.getZ() - 4.5) + " 0 30");
            ctx.waitTicks(30);
            ctx.takeScreenshot("markt-17-umrisse");
        }
    }

    /** Befehl als Spieler (die Admin-Befehle brauchen einen Spieler mit Auswahl). */
    private static void playerCommand(ClientGameTestContext ctx, String cmd) {
        ctx.runOnClient(mc -> mc.player.connection.sendCommand(cmd));
        ctx.waitTicks(5);
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Text in ein Eingabefeld schreiben (gefunden über den Hinweistext). */
    private static void type(ClientGameTestContext ctx, String hint, String text) {
        boolean ok = ctx.computeOnClient(mc -> mc.screen != null && typeIn(mc.screen.children(), hint, text));
        if (!ok) throw new AssertionError("Eingabefeld nicht gefunden: " + hint);
    }

    private static boolean typeIn(List<? extends GuiEventListener> children, String hint, String text) {
        for (var child : children) {
            if (child instanceof EditBox e && e.getMessage().getString().equals(hint)) {
                e.setValue(text);
                return true;
            }
            if (child instanceof ContainerEventHandler c && typeIn(c.children(), hint, text)) return true;
        }
        return false;
    }

    /** Kit-Knöpfe sind {@code AbstractButton}, nicht {@code Button} – {@code tryClickScreenButton} findet sie nicht. */
    private static void press(ClientGameTestContext ctx, String label, boolean prefix) {
        boolean ok = ctx.computeOnClient(mc -> mc.screen != null && pressIn(mc.screen.children(), label, prefix));
        if (!ok) throw new AssertionError("Knopf nicht gefunden: " + label + (prefix ? "…" : ""));
    }

    private static boolean pressIn(List<? extends GuiEventListener> children, String label, boolean prefix) {
        for (var child : children) {
            if (child instanceof AbstractButton b && b.visible && b.active) {
                String text = b.getMessage().getString();
                if (prefix ? text.startsWith(label) : text.equals(label)) {
                    b.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0));
                    return true;
                }
            }
            if (child instanceof ContainerEventHandler c && pressIn(c.children(), label, prefix)) return true;
        }
        return false;
    }
}
