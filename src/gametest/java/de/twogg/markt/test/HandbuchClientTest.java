package de.twogg.markt.test;

import de.twogg.markt.content.guide.GuideBook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Markt-Handbuch (seit 0.3.0): beim ersten Betreten im Inventar, /markt handbuch gibt ein weiteres, das Buch öffnet sich
 * als echtes Buch (Seiten blättern, Inhaltsverzeichnis springt per Klick). Bilder: Titel, Inhalt, Kapitelseiten, Item.
 */
public final class HandbuchClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            ctx.waitTicks(40);
            int onJoin = sp.getServer().computeOnServer(s -> count(s.getPlayerList().getPlayers().get(0)));
            check(onJoin == 1, "Beim ersten Betreten: Handbuch im Inventar – " + onJoin);

            sp.getServer().runCommand("markt handbuch");
            ctx.waitTicks(5);
            int after = sp.getServer().computeOnServer(s -> count(s.getPlayerList().getPlayers().get(0)));
            check(after == 2, "/markt handbuch gibt ein weiteres – " + after);

            int pages = sp.getServer().computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                ItemStack book = first(p);
                p.setItemInHand(InteractionHand.MAIN_HAND, book.copy());
                var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
                return content == null ? 0 : content.pages().size();
            });
            check(pages >= 10, "Buch hat genug Seiten – " + pages);
            ctx.waitTicks(5);
            ctx.takeScreenshot("handbuch-0-item");

            // wie Rechtsklick: Server öffnet das Buch beim Client
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                p.openItemGui(p.getMainHandItem(), InteractionHand.MAIN_HAND);
            });
            ctx.waitTicks(20);
            boolean open = ctx.computeOnClient(mc -> mc.screen instanceof BookViewScreen);
            check(open, "Buch öffnet sich als Buchansicht");
            ctx.takeScreenshot("handbuch-1-titel");
            ctx.runOnClient(mc -> ((BookViewScreen) mc.screen).setPage(1));
            ctx.waitTicks(5);
            ctx.takeScreenshot("handbuch-2-inhalt");
            for (int page : new int[] {2, 3, 5, 8}) {
                if (page < pages) {
                    ctx.runOnClient(mc -> ((BookViewScreen) mc.screen).setPage(page));
                    ctx.waitTicks(5);
                    ctx.takeScreenshot("handbuch-3-seite-" + (page + 1));
                }
            }
            ctx.runOnClient(mc -> mc.setScreen(null));
        }
    }

    private static int count(ServerPlayer p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            if (GuideBook.is(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static ItemStack first(ServerPlayer p) {
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
            if (GuideBook.is(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private static void check(boolean ok, String what) {
        System.out.println("[HANDBUCH-TEST] " + (ok ? "OK     " : "FEHLER ") + what);
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
