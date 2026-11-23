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
 * Seiten der Marktverwaltung – mit Screenshots und Prüfungen auf dem Server. Kulisse ist eine verschneite
 * Weihnachtsmarkt-Gasse ({@link MarktKulisse}); am Ende die Abnahme der Modelle in einem Schauraum (Tag/Nacht).
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
            // Seit Economy 0.3 läuft die Bank nur in einem Kit-Event – ohne Event schlägt jede Buchung fehl
            sp.getServer().runCommand("event schnellstart Markt-Test");
            ctx.waitTicks(10);
            if (!sp.getServer().computeOnServer(s -> de.twogg.economy.api.Economy.available())) {
                throw new AssertionError("Event-Schnellstart hat die Economy nicht aktiviert");
            }
            BlockPos base = ctx.computeOnClient(mc -> mc.player.blockPosition());
            // Winterlandschaft (Schneebiom, Schneedecke) und die Weihnachtsmarkt-Gasse als Kulisse für die Bilder
            String x0 = String.valueOf(base.getX() - 48), x1 = String.valueOf(base.getX() + 48);
            String z0 = String.valueOf(base.getZ() - 48), z1 = String.valueOf(base.getZ() + 48);
            sp.getServer().runCommand("fillbiome " + x0 + " " + (base.getY() - 1) + " " + z0 + " " + x1 + " " + base.getY() + " " + z1
                    + " minecraft:snowy_plains");
            sp.getServer().runCommand("fill " + x0 + " " + base.getY() + " " + z0 + " " + x1 + " " + base.getY() + " " + z1
                    + " minecraft:snow replace minecraft:air");
            sp.getServer().runOnServer(server -> MarktKulisse.gasse(server.overworld(), base));
            sp.getServer().runCommand("tp @p " + (base.getX() + 0.5) + " " + base.getY() + " " + (base.getZ() + 0.5) + " 0 25");
            ctx.waitTicks(20);
            sp.getClientLevel().waitForChunksRender();

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
            shot(ctx, "markt-01-schild");

            // 2) Standschild per Rechtsklick → Marktfenster mit dem Stand vorgewählt
            ctx.getInput().pressKey(options -> options.keyUse);
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("MarketScreen"), 100);
            ctx.waitTicks(10);
            shot(ctx, "markt-02-staende");
            long bal0 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            sp.getServer().runOnServer(server -> Economy.pay(player(server), Money.ofCoins(50), "test", "Startkapital"));
            ctx.waitTicks(2);
            press(ctx, "Mieten", true);
            ctx.waitTicks(15);
            shot(ctx, "markt-03-gemietet");
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
            shot(ctx, "markt-04-mein-stand");
            press(ctx, "Verdienen", false);
            ctx.waitTicks(5);
            shot(ctx, "markt-05-verdienen");
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
            shot(ctx, "markt-06-theke-leer");
            type(ctx, "Preis", "2");
            type(ctx, "Stück", "4");
            press(ctx, "Anbieten", false);
            ctx.waitTicks(10);
            shot(ctx, "markt-07-theke-angebot");
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
            shot(ctx, "markt-08-theke-verwalten");
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
            sp.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(base.offset(0, 0, 5), Blocks.AIR.defaultBlockState()));

            // 6) Ankauf: 16 Fichtenstämme verkaufen
            BlockPos buyer = base.offset(MarktKulisse.BUYER);
            sp.getServer().runOnServer(server -> {
                server.overworld().setBlockAndUpdate(buyer, ModContent.BUYER.defaultBlockState());
                ServerPlayer p = player(server);
                p.getInventory().add(new ItemStack(Items.SPRUCE_LOG, 16));
                BuyerService.open(p, buyer);
            });
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("BuyerScreen"), 100);
            ctx.waitTicks(5);
            shot(ctx, "markt-09-ankauf");
            long bal4 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
            press(ctx, "Alle", false);   // erste Zeile: Fichtenholz, 8 Stück für 0,2
            ctx.waitTicks(10);
            shot(ctx, "markt-10-ankauf-verkauft");
            String sold = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.SPRUCE_LOG)
                    + "/" + (Economy.balance(player(server)) - bal4));
            check(sold.equals("0/4"), "Ankauf 2 × 8 Fichtenstämme = 0,4 WLC: " + sold);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 7) Auftragsbrett: Marktauftrag liefern, eigenen Auftrag aushängen
            BlockPos board = base.offset(MarktKulisse.BOARD);
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
            shot(ctx, "markt-11-auftragsbrett");
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
            shot(ctx, "markt-12-aushaengen");
            press(ctx, "Auftrag aushängen", false);
            ctx.waitTicks(10);
            String posted = sp.getServer().computeOnServer(server -> {
                Job j = MarktRuntime.data().jobs.stream().filter(x -> !x.system).findFirst().orElse(null);
                return j == null ? "" : j.title + "/" + j.count + "/" + j.reward + "/" + (bal6 - Economy.balance(player(server)));
            });
            check(posted.equals("Laternen für meine Bude/4/30/30"), "Spielerauftrag: " + posted);
            press(ctx, "Aufträge", false);
            ctx.waitTicks(5);
            shot(ctx, "markt-13-auftraege");
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
            shot(ctx, "markt-14-abholfach");
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
            shot(ctx, "markt-14b-theke-kaeufer");
            press(ctx, "Kaufen", true);
            ctx.waitTicks(10);
            shot(ctx, "markt-14c-theke-gekauft");
            String cashBuy = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(Items.COOKIE)
                    + "/" + Economy.cash(player(server)) + "/" + MarktRuntime.data().plots.get("gluehweinbude").till);
            // 2 × 3 Kekse für 2 × 1,5 = 3 WLC bar: 10 − 3 = 7 WLC Bargeld; Kasse 3 − 0,2 Gebühr = 2,8
            check(cashBuy.equals("6/70/28"), "Barkauf an der Theke (Kekse/Bargeld/Kasse): " + cashBuy);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);

            // 9b2) Hitboxen drehen mit der Blickrichtung (Auftragsbrett: breit quer zur Blickrichtung, schmal entlang)
            String shapes = sp.getServer().computeOnServer(server -> {
                StringBuilder sb = new StringBuilder();
                for (Direction d : new Direction[]{Direction.NORTH, Direction.EAST}) {
                    var st = ModContent.BOARD.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
                    var bb = st.getShape(server.overworld(), BlockPos.ZERO).bounds();
                    sb.append(d).append(':').append(Math.round((bb.maxX - bb.minX) * 16)).append('x').append(Math.round((bb.maxZ - bb.minZ) * 16)).append(' ');
                }
                return sb.toString().trim();
            });
            check(shapes.equals("north:16x3 east:3x16"), "Hitbox Auftragsbrett (Breite x Tiefe): " + shapes);

            // 9c) Hologramme über den Ständen
            String holo = sp.getServer().computeOnServer(server -> {
                de.twogg.markt.server.Holograms.tick();
                StringBuilder sb = new StringBuilder();
                for (Plot p : MarktRuntime.data().plots.values()) {
                    var e = p.hologram.isEmpty() ? null : server.overworld().getEntity(UUID.fromString(p.hologram));
                    String text = e instanceof net.minecraft.world.entity.Display.TextDisplay td
                            ? ((de.twogg.markt.mixin.TextDisplayAccessor) td).markt$getText().getString() : "-";
                    sb.append(p.id).append('=').append(text.replace('\n', '|')).append(';');
                }
                return sb.toString();
            });
            check(holo.contains("gluehweinbude=Glühweinbude|Stand von Kunde") && holo.contains("lebkuchenstand=Lebkuchenstand|frei"),
                    "Hologramme: " + holo);

            // 9d) Weihnachtsmarkt-Event: Start, Losbude, Geschenk, Wichteln, Abstimmen, Siegerehrung
            BlockPos lottery = base.offset(MarktKulisse.LOTTERY), gift = base.offset(MarktKulisse.GIFT), advent = base.offset(MarktKulisse.ADVENT);
            UUID kunde = UUID.nameUUIDFromBytes("kunde".getBytes());
            String started = sp.getServer().computeOnServer(server -> {
                ServerLevel l = server.overworld();
                l.setBlockAndUpdate(lottery, ModContent.LOTTERY.defaultBlockState());
                l.setBlockAndUpdate(gift, ModContent.GIFT.defaultBlockState());
                l.setBlockAndUpdate(advent, ModContent.ADVENT.defaultBlockState());
                return de.twogg.markt.server.EventService.start(MarktRuntime.now() + 30 * 60_000L, null, player(server));
            });
            check(sp.getServer().computeOnServer(server -> MarktRuntime.data().event.running()), "Event läuft nicht: " + started);
            sp.getServer().runCommand("weather clear");   // das Event lässt es schneien – für die Bilder klare Sicht
            camera(sp, base, 7.5, 0, -6.5, 40, -2);
            ctx.waitTicks(40);
            shot(ctx, "markt-18-event-eroeffnung");
            camera(sp, base, 0.5, 0, 0.5, 0, 25);
            ctx.waitTicks(5);
            sp.getServer().runOnServer(server -> de.twogg.markt.server.ActivityService.openLottery(player(server), lottery));
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("ActivityScreen"), 100);
            ctx.waitTicks(5);
            press(ctx, "Mehr", false);
            ctx.waitTicks(2);
            shot(ctx, "markt-19-losbude");
            press(ctx, "2 Lose kaufen", true);
            ctx.waitTicks(10);
            shot(ctx, "markt-20-lose-gekauft");
            String lots = sp.getServer().computeOnServer(server -> {
                var e = MarktRuntime.data().event;
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(kunde, "Kunde"));
                f.setPos(lottery.getX() + 0.5, lottery.getY(), lottery.getZ() - 1.5);
                Sessions.open(f, "lottery", lottery, ModContent.LOTTERY, null);
                Views.Action act = Views.Action.of("buy");
                act.screen = "lottery";
                act.count = 3;
                de.twogg.markt.server.ActivityService.handle(f, act);
                return e.tickets.getOrDefault(player(server).getUUID().toString(), 0) + "/" + e.tickets.getOrDefault(kunde.toString(), 0) + "/" + e.pot;
            });
            check(lots.equals("2/3/25"), "Lose (Spieler/Kunde/Topf in Zehnteln, 5 € = 0,5 WLC): " + lots);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);
            String found = sp.getServer().computeOnServer(server -> {
                long before = Economy.balance(player(server));
                de.twogg.markt.server.ActivityService.openGift(player(server), gift);
                de.twogg.markt.server.ActivityService.openGift(player(server), gift);   // zweites Mal zählt nicht
                return (Economy.balance(player(server)) - before) + "/" + MarktRuntime.data().event.stats.get(player(server).getUUID().toString()).gifts;
            });
            check(found.equals("5/1"), "Geschenk gefunden (Belohnung/Zähler): " + found);
            sp.getServer().runOnServer(server -> {
                player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND, 2));
                de.twogg.markt.server.ActivityService.openLottery(player(server), lottery);
            });
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("ActivityScreen"), 100);
            ctx.waitTicks(5);
            press(ctx, "Wichteln", false);
            ctx.waitTicks(3);
            shot(ctx, "markt-21-wichteln");
            press(ctx, "Geschenk abgeben", false);
            ctx.waitTicks(10);
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);
            String wich = sp.getServer().computeOnServer(server -> {
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(kunde, "Kunde"));
                f.setPos(lottery.getX() + 0.5, lottery.getY(), lottery.getZ() - 1.5);
                f.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PUMPKIN_PIE, 3));
                return "";
            });
            ctx.waitTicks(1);
            String wich2 = sp.getServer().computeOnServer(server -> {
                FakePlayer f = FakePlayer.get(server.overworld(), new GameProfile(kunde, "Kunde"));
                Sessions.open(f, "lottery", lottery, ModContent.LOTTERY, null);
                Views.Action act = Views.Action.of("give");
                act.screen = "lottery";
                de.twogg.markt.server.ActivityService.handle(f, act);
                return MarktRuntime.data().event.wichtel.size() + "/" + player(server).getMainHandItem().isEmpty();
            });
            check(wich2.equals("2/true"), "Wichtelgeschenke unter dem Baum / Hand leer: " + wich2 + wich);
            String vote = sp.getServer().computeOnServer(server -> de.twogg.markt.server.EventService.vote(player(server), "gluehweinbude")[0]);
            check(vote.contains("Glühweinbude"), "Abstimmen: " + vote);
            sp.getServer().runOnServer(server -> {
                de.twogg.markt.server.Holograms.tick();
                de.twogg.markt.server.ActivityService.openAdvent(player(server), advent);
            });
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("ActivityScreen"), 100);
            ctx.waitTicks(5);
            shot(ctx, "markt-22-adventskalender");
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);
            ctx.runOnClient(mc -> mc.player.connection.sendCommand("markt"));
            ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("MarketScreen"), 100);
            ctx.waitTicks(5);
            press(ctx, "Event", false);
            ctx.waitTicks(5);
            shot(ctx, "markt-23-event-seite");
            press(ctx, "Fertig", false);
            ctx.waitFor(mc -> mc.screen == null, 100);
            playerCommand(ctx, "markt event tafel hier");
            fly(sp);   // ab hier nur noch Prüfungen ohne Bauschutz/Räumdienst: Kamera schwebt
            camera(sp, base, 0.5, 2, -7.5, 0, 6);
            sp.getServer().runOnServer(server -> de.twogg.markt.server.Holograms.tick());
            ctx.waitTicks(30);
            sp.getClientLevel().waitForChunksRender();
            shot(ctx, "markt-24-hologramme");
            // Siegerehrung
            String fin = sp.getServer().computeOnServer(server -> {
                long before = Economy.balance(player(server));
                de.twogg.markt.server.EventService.finale();
                var e = MarktRuntime.data().event;
                var box = MarktRuntime.data().pickup.get(player(server).getUUID().toString());
                boolean gotPie = box != null && box.stream().anyMatch(j -> j.toString().contains("pumpkin_pie"));
                var kBox = MarktRuntime.data().pickup.get(kunde.toString());
                boolean kGotDiamond = kBox != null && kBox.stream().anyMatch(j -> j.toString().contains("diamond"));
                var pend = MarktRuntime.data().pending.get(kunde.toString());
                return e.phase + "/" + e.pot + "/" + gotPie + "/" + kGotDiamond + "/" + (pend != null && pend.amount >= 100)
                        + "/" + (Economy.balance(player(server)) > before) + "/" + e.lastResults.size();
            });
            // Phase FINALE, Topf ausgeschüttet, Wichtel getauscht, Kunde bekommt (offline) den Preis für den schönsten Stand,
            // der Spieler gewinnt mindestens Geschenkesuche + einen Lospreis
            check(fin.startsWith("FINALE/0/true/true/true/true/"), "Siegerehrung: " + fin);
            ctx.waitTicks(20);
            shot(ctx, "markt-25-siegerehrung");
            sp.getServer().runOnServer(server -> de.twogg.markt.server.EventService.close());

            // 10) Marktverwaltung: alle Seiten
            ctx.runOnClient(mc -> mc.player.connection.sendCommand("markt admin"));
            ctx.waitFor(mc -> mc.screen instanceof AdminScreen, 100);
            ctx.waitTicks(10);
            shot(ctx, "markt-15-admin-staende");
            for (String page : new String[]{"Neu", "Event", "Ankauf", "Aufträge", "Räumzonen", "Regeln"}) {
                press(ctx, page, false);
                ctx.waitTicks(5);
                shot(ctx, "markt-16-admin-" + page.toLowerCase().replace("ä", "ae"));
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

            // 11) Planer in der Hand: Umrisse sichtbar (von oben, schwebend)
            sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.PLANNER)));
            fly(sp);
            camera(sp, base, 5.5, 6.5, -3.5, 0, 42);
            ctx.waitTicks(30);
            shot(ctx, "markt-17-umrisse");

            // 12) Server-Laden (0.4.0): per Befehl anlegen, Ware aus der Hand, Fenster per /markt laden oeffnen
            laden(ctx, sp);

            // 13) Abnahme der Modelle: jeder Block in mehreren Ausrichtungen, Tag und Nacht, aus drei Blickwinkeln
            abnahme(ctx, sp, base);
        }
    }

    private static void laden(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode survival @a");
        sp.getServer().runCommand("markt laden neu lager Materiallager");
        sp.getServer().runCommand("markt laden untertitel lager Brandt · Station 7");
        sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.TORCH)));
        sp.getServer().runCommand("execute as @p run markt laden verkauf lager 0.5 4");
        sp.getServer().runOnServer(server -> player(server).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.COAL)));
        sp.getServer().runCommand("execute as @p run markt laden ankauf lager 1.2 8 2");
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = player(server);
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.COAL, 20));
            Economy.pay(p, Money.ofCoins(10), "test", "Laden");
        });
        String lines = sp.getServer().computeOnServer(server -> {
            var l = MarktRuntime.data().laeden.get("lager");
            return l == null ? "kein Laden" : l.sells.size() + "/" + l.buys.size();
        });
        check(lines.equals("1/1"), "Laden mit 1 Verkauf und 1 Ankauf erwartet: " + lines);
        // wie ein Storyteller-NPC: Befehl von der Konsole für den Spieler
        sp.getServer().runCommand("markt laden oeffnen lager @p");
        ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("LadenScreen"), 100);
        ctx.waitTicks(10);
        shot(ctx, "markt-18-laden-kaufen");
        long before = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
        press(ctx, "Kaufen ·", true);
        ctx.waitTicks(10);
        String bought = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(net.minecraft.world.item.Items.TORCH)
                + "/" + (before - Economy.balance(player(server))));
        check(bought.equals("4/5"), "Kauf 4 Fackeln für 0,5 erwartet (Stück/Zehntel): " + bought);
        press(ctx, "Verkaufen", false);
        ctx.waitTicks(5);
        shot(ctx, "markt-19-laden-verkaufen");
        long before2 = sp.getServer().computeOnServer(server -> Economy.balance(player(server)));
        press(ctx, "Alle", false);
        ctx.waitTicks(10);
        String sold = sp.getServer().computeOnServer(server -> player(server).getInventory().countItem(net.minecraft.world.item.Items.COAL)
                + "/" + (Economy.balance(player(server)) - before2));
        check(sold.equals("4/24"), "Ankauf: Tageslimit 2 × 8 Kohle für je 1,2 erwartet (Rest/Zehntel): " + sold);
        press(ctx, "Fertig", false);
        ctx.waitFor(mc -> mc.screen == null, 100);

        // 0.5.0: Waren mit Lager – Import (inkl. Verzauberungsbuch mit Daten), Preis wandert, Lager voll, Lieferung
        String imp = sp.getServer().computeOnServer(server -> {
            try {
                java.nio.file.Path f = de.twogg.markt.data.Store.configFile().getParent().resolve("testlager.json");
                java.nio.file.Files.createDirectories(f.getParent());
                java.nio.file.Files.writeString(f, "{\"spread\":0.6,\"waren\":["
                        + "{\"item\":\"minecraft:cobblestone\",\"einheit\":64,\"preis\":0.5,\"lager\":256,\"soll\":128},"
                        + "{\"stack\":{\"id\":\"minecraft:enchanted_book\",\"count\":1,\"components\":{\"minecraft:stored_enchantments\":{\"minecraft:mending\":1}}},"
                        + "\"name\":\"Reparatur\",\"einheit\":1,\"preis\":12,\"lager\":4,\"soll\":1},"
                        + "{\"item\":\"gibtsnicht:holz\",\"preis\":1}]}");
                var l = MarktRuntime.data().laeden.get("lager");
                return String.join(" | ", de.twogg.markt.server.LadenService.importFile(server.registryAccess(), l, "testlager"));
            } catch (Exception e) {
                return e.toString();
            }
        });
        check(imp.startsWith("2 Waren übernommen") && imp.contains("gibtsnicht:holz"), "Import: " + imp);
        String book = sp.getServer().computeOnServer(server -> {
            var l = MarktRuntime.data().laeden.get("lager");
            var w = l.waren.get(1);
            ItemStack st = de.twogg.markt.data.Stacks.fromJson(server.registryAccess(), w.item);
            return st.getItem() + " " + st.getComponentsPatch().isEmpty() + " " + w.stock;
        });
        check(book.contains("enchanted_book false 1"), "Buch mit Reparatur und Bestand 1: " + book);
        String flow = sp.getServer().computeOnServer(server -> {
            ServerPlayer p = player(server);
            var l = MarktRuntime.data().laeden.get("lager");
            var w = l.waren.get(0);
            Economy.pay(p, Money.ofCoins(20), "test", "Lager");
            long p0 = de.twogg.markt.core.StockMath.sellPrice(w.base, w.stock, w.target, w.capacity);
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64 * 5));
            long bal = Economy.balance(p);
            de.twogg.markt.net.Views.Action a = de.twogg.markt.net.Views.Action.of("sell");
            a.id = w.id;
            a.flag = true;
            a.screen = "laden";
            de.twogg.markt.server.Sessions.open(p, "laden", p.blockPosition(), null, "lager");
            de.twogg.markt.server.LadenService.handle(p, a);
            long earned = Economy.balance(p) - bal;
            int left = p.getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE);
            long stockFull = w.stock;
            long p1 = de.twogg.markt.core.StockMath.sellPrice(w.base, w.stock, w.target, w.capacity);
            de.twogg.markt.server.LadenService.deliver(l);
            return earned + "/" + left + "/" + stockFull + "/" + (p1 < p0) + "/" + w.stock;
        });
        // 2 Stacks passen (128 → 256), Ankauf 0,3 × Faktor 1 bzw. ≈0,71 → 3 + 2 = 5 Zehntel; 3 Stacks bleiben übrig
        check(flow.equals("5/192/256/true/128"), "Ankauf bis Lager voll, Preis fällt, Lieferung auf Soll: " + flow);
        // die echte Liste für Brandt: alles Vanilla wird erkannt, nur die Mod-Platzhalter (MODID:) fehlen
        String real = sp.getServer().computeOnServer(server -> {
            try {
                java.nio.file.Path src = java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../../beispiele/materiallager.json").normalize();
                java.nio.file.Files.copy(src, de.twogg.markt.data.Store.configFile().getParent().resolve("materiallager.json"),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                de.twogg.markt.server.LadenService.create("materiallager", "Materiallager");
                var l = MarktRuntime.data().laeden.get("materiallager");
                var out = de.twogg.markt.server.LadenService.importFile(server.registryAccess(), l, "materiallager");
                boolean onlyMod = out.stream().skip(1).allMatch(x -> x.contains("MODID:"));
                return out.get(0) + " · nur Mod-Platzhalter: " + onlyMod + " · " + l.waren.size();
            } catch (Exception e) {
                return e.toString();
            }
        });
        check(real.startsWith("100 Waren übernommen, 9 übersprungen") && real.contains("nur Mod-Platzhalter: true"), "Materiallager-Liste: " + real);
        sp.getServer().runCommand("markt laden oeffnen lager @p");
        ctx.waitFor(mc -> mc.screen != null && mc.screen.getClass().getSimpleName().equals("LadenScreen"), 100);
        ctx.waitTicks(10);
        shot(ctx, "markt-20-lager-kaufen");
        press(ctx, "Verkaufen", false);
        ctx.waitTicks(5);
        shot(ctx, "markt-21-lager-verkaufen");
        press(ctx, "Fertig", false);
        ctx.waitFor(mc -> mc.screen == null, 100);
    }

    /**
     * Schauraum mit allen Markt-Blöcken (Reihe A nach Norden, Reihe B gedreht) – Bilder bei Tag und Nacht, Nahaufnahmen
     * und die Item-Ansicht im Inventar. Prüft, dass jeder Block mit seiner Ausrichtung steht.
     */
    private static void abnahme(ClientGameTestContext ctx, TestSingleplayerContext sp, BlockPos base) {
        BlockPos c = sp.getServer().computeOnServer(server -> MarktKulisse.schauraum(server.overworld(), base));
        String placed = sp.getServer().computeOnServer(server -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) {
                var st = server.overworld().getBlockState(c.offset(-9 + i * 3, i == 6 ? 1 : 0, 5));
                sb.append(st.getBlock() instanceof de.twogg.markt.content.MarktBlock ? st.getValue(HorizontalDirectionalBlock.FACING).getName().charAt(0) : '-');
            }
            sb.append('/');
            for (int i = 0; i < 7; i++) {
                var st = server.overworld().getBlockState(c.offset(9 - i * 3, i == 6 ? 1 : 0, -5));
                sb.append(st.getBlock() instanceof de.twogg.markt.content.MarktBlock ? st.getValue(HorizontalDirectionalBlock.FACING).getName().charAt(0) : '-');
            }
            return sb.toString();
        });
        check(placed.equals("nnnnnnn/seswess"), "Schauraum (Ausrichtungen Reihe A/B): " + placed);
        BlockPos rel = c.subtract(base);
        String[] zeit = {"noon", "midnight"};
        for (int t = 0; t < 2; t++) {
            String tag = t == 0 ? "tag" : "nacht";
            sp.getServer().runCommand("time set " + zeit[t]);
            sp.getServer().runCommand("weather clear");
            camera(sp, base, rel.getX() + 0.5, 2.5, rel.getZ() - 3.5, 0, 14);
            ctx.waitTicks(30);
            sp.getClientLevel().waitForChunksRender();
            shot(ctx, "abnahme-" + tag + "-1-vorn");
            camera(sp, base, rel.getX() + 0.5, 2.5, rel.getZ() + 3.5, 180, 14);
            ctx.waitTicks(15);
            shot(ctx, "abnahme-" + tag + "-2-gedreht");
            camera(sp, base, rel.getX() - 14.5, 6, rel.getZ() + 0.5, -90, 30);
            ctx.waitTicks(15);
            shot(ctx, "abnahme-" + tag + "-3-seite");
            if (t == 0) {
                camera(sp, base, rel.getX() - 4.5, 1.2, rel.getZ() + 1.2, -10, 8);
                ctx.waitTicks(15);
                shot(ctx, "abnahme-tag-4-nah-links");
                camera(sp, base, rel.getX() + 5.5, 1.2, rel.getZ() + 1.2, 10, 8);
                ctx.waitTicks(15);
                shot(ctx, "abnahme-tag-5-nah-rechts");
                camera(sp, base, rel.getX() + 0.5, 1.2, rel.getZ() - 1.8, 180, 8);
                ctx.waitTicks(15);
                shot(ctx, "abnahme-tag-6-nah-gedreht");
                // Nahaufnahme jedes Blocks (schräg von vorn rechts)
                String[] namen = {"schild", "theke", "ankauf", "brett", "losbude", "geschenk", "advent"};
                for (int i = 0; i < namen.length; i++) {
                    double[] h = {1.2, 1.5, 1.6, 1.7, 1.7, 0.6, 2.1};
                    camera(sp, base, rel.getX() - 9 + i * 3 + 0.5 - 1.1, h[i], rel.getZ() + 3.6, -32, i == 5 ? 35 : 18);
                    ctx.waitTicks(8);
                    shot(ctx, "abnahme-tag-7-nah-" + namen[i]);
                }
            }
        }
        // Item-Ansicht: alle Markt-Items im Inventar
        sp.getServer().runCommand("time set noon");
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = player(server);
            p.getInventory().clearContent();
            net.minecraft.world.item.Item[] items = {ModContent.STALL_SIGN_ITEM, ModContent.COUNTER_ITEM, ModContent.BUYER_ITEM,
                    ModContent.BOARD_ITEM, ModContent.LOTTERY_ITEM, ModContent.GIFT_ITEM, ModContent.ADVENT_ITEM, ModContent.PLANNER};
            for (int i = 0; i < items.length; i++) {
                p.getInventory().setItem(i, new ItemStack(items[i]));
                p.getInventory().setItem(9 + i, new ItemStack(items[i]));
            }
        });
        sp.getServer().runCommand("gamemode survival @a");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        ctx.waitTicks(10);
        shot(ctx, "abnahme-gui");
        ctx.runOnClient(mc -> mc.setScreen(null));
    }

    /** Spieler (= Kamera) an eine Stelle relativ zur Basis setzen. */
    private static void camera(TestSingleplayerContext sp, BlockPos base, double x, double y, double z, float yaw, float pitch) {
        sp.getServer().runCommand("tp @p " + (base.getX() + x) + " " + (base.getY() + y) + " " + (base.getZ() + z) + " " + yaw + " " + pitch);
    }

    /** Kreativmodus und Fliegen, damit die Kamera in der Luft stehen bleibt (nur noch für Bilder am Ende). */
    private static void fly(TestSingleplayerContext sp) {
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = player(server);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
    }

    /** Befehl als Spieler (die Admin-Befehle brauchen einen Spieler mit Auswahl). */
    private static void playerCommand(ClientGameTestContext ctx, String cmd) {
        ctx.runOnClient(mc -> mc.player.connection.sendCommand(cmd));
        ctx.waitTicks(5);
    }

    /**
     * Sauberes Bild (auch fürs Markt-Handbuch, tools/handbuch_bilder.py): ohne Meldungen und Chat, Weltbilder ohne HUD.
     */
    private static void shot(ClientGameTestContext ctx, String name) {
        boolean world = ctx.computeOnClient(mc -> mc.screen == null);
        ctx.runOnClient(mc -> {
            mc.getToastManager().clear();
            mc.gui.getChat().clearMessages(false);
            if (world) mc.options.hideGui = true;
        });
        ctx.waitTicks(3);
        ctx.takeScreenshot(name);
        if (world) ctx.runOnClient(mc -> mc.options.hideGui = false);
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
