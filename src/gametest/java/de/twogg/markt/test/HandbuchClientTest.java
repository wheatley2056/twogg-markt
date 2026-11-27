package de.twogg.markt.test;

import de.twogg.markt.client.HandbookScreen;
import de.twogg.markt.content.guide.GuideBook;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Markt-Handbuch (seit 0.3.0, eigenes Buch): beim ersten Betreten im Inventar, /markt handbuch gibt ein weiteres,
 * Rechtsklick öffnet das eigene Buch; Blättern, Inhaltsverzeichnis, Lesezeichen und Bild-Zoom. Bilder jeder Doppelseite.
 */
public final class HandbuchClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getClientLevel().waitForChunksRender();
            ctx.waitTicks(40);
            int onJoin = sp.getServer().computeOnServer(s -> count(s.getPlayerList().getPlayers().get(0)));
            check(onJoin == 1, "Beim ersten Betreten: Handbuch im Inventar – " + onJoin);

            sp.getServer().runCommand("execute as @p run markt handbuch");
            ctx.waitTicks(5);
            int after = sp.getServer().computeOnServer(s -> count(s.getPlayerList().getPlayers().get(0)));
            check(after == 2, "/markt handbuch gibt ein weiteres – " + after);

            // Rechtsklick mit dem Buch in der Hand (wie ein Spieler)
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                p.setItemInHand(InteractionHand.MAIN_HAND, GuideBook.create());
                p.getMainHandItem().use(p.level(), p, InteractionHand.MAIN_HAND);
            });
            ctx.waitTicks(20);
            boolean open = ctx.computeOnClient(mc -> mc.screen instanceof HandbookScreen);
            check(open, "Rechtsklick öffnet das eigene Buch");
            int pages = ctx.computeOnClient(mc -> ((HandbookScreen) mc.screen).pages());
            check(pages >= 12, "Buch hat genug Seiten – " + pages);
            ctx.takeScreenshot("handbuch-01-titel-inhalt");

            // Blättern mit der Pfeiltaste
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT);
            ctx.waitTicks(5);
            int s1 = ctx.computeOnClient(mc -> ((HandbookScreen) mc.screen).spread());
            check(s1 == 1, "Pfeiltaste blättert weiter – Doppelseite " + s1);
            ctx.takeScreenshot("handbuch-02-kapitel-1");

            // Kapitel über das Lesezeichen
            String chapter = ctx.computeOnClient(mc -> ((HandbookScreen) mc.screen).chapters().get(5));
            int target = ctx.computeOnClient(mc -> ((HandbookScreen) mc.screen).chapterPage(chapter));
            ctx.runOnClient(mc -> ((HandbookScreen) mc.screen).goTo(target));
            ctx.waitTicks(5);
            int s2 = ctx.computeOnClient(mc -> ((HandbookScreen) mc.screen).spread());
            check(s2 == target / 2, "Sprung zu „" + chapter + "“ – Doppelseite " + s2);
            ctx.takeScreenshot("handbuch-03-weihnachtsmarkt");

            int total = (pages + 1) / 2;
            for (int sp2 = 2; sp2 < total; sp2 += 2) {
                int page = sp2 * 2;
                ctx.runOnClient(mc -> ((HandbookScreen) mc.screen).goTo(page));
                ctx.waitTicks(4);
                ctx.takeScreenshot("handbuch-04-doppelseite-" + sp2);
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

    private static void check(boolean ok, String what) {
        System.out.println("[HANDBUCH-TEST] " + (ok ? "OK     " : "FEHLER ") + what);
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
