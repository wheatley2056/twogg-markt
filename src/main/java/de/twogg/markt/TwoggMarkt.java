package de.twogg.markt;

import de.twogg.markt.content.ModContent;
import de.twogg.markt.net.Payloads;
import de.twogg.markt.net.Views;
import de.twogg.markt.server.ActivityService;
import de.twogg.markt.server.AdminService;
import de.twogg.markt.server.EventService;
import de.twogg.markt.server.Holograms;
import de.twogg.markt.server.BoardService;
import de.twogg.markt.server.BuyerService;
import de.twogg.markt.server.LadenService;
import de.twogg.markt.server.MarktCommands;
import de.twogg.markt.server.Outlines;
import de.twogg.markt.server.Pay;
import de.twogg.markt.server.Protection;
import de.twogg.markt.server.Sessions;
import de.twogg.markt.server.ShopService;
import de.twogg.markt.server.StallService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TwoGG Markt – Weihnachtsmarkt für Winterland: Admins zeichnen Stände ein, Spieler mieten oder kaufen sie und
 * verkaufen an ihrer Theke. Dazu Nebenverdienste: Ankauf, Auftragsbrett und Räumdienst. Geld über TwoGG Economy.
 */
public class TwoggMarkt implements ModInitializer {
    public static final String MODID = "twogg_markt";
    public static final Logger LOGGER = LoggerFactory.getLogger("TwoGG Markt");

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    @Override
    public void onInitialize() {
        ModContent.init();
        de.twogg.markt.content.guide.GuideBook.init();
        PayloadTypeRegistry.clientboundPlay().register(Payloads.View.TYPE, Payloads.View.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.Action.TYPE, Payloads.Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Payloads.Action.TYPE, (p, ctx) -> handle(ctx.player(), p.json()));
        Protection.register();
        Holograms.register();

        ServerLifecycleEvents.SERVER_STARTED.register(MarktRuntime::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> MarktRuntime.stop());
        ServerLifecycleEvents.AFTER_SAVE.register((s, flush, force) -> MarktRuntime.save());
        ServerTickEvents.END_SERVER_TICK.register(s -> MarktRuntime.tick());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            if (!MarktRuntime.running()) return;
            Pay.settle(handler.getPlayer());
            EventService.join(handler.getPlayer());
            de.twogg.markt.content.guide.GuideService.join(handler.getPlayer());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            Sessions.forget(handler.getPlayer().getUUID());
            Outlines.forget(handler.getPlayer().getUUID());
            Protection.forget(handler.getPlayer().getUUID());
            EventService.forget(handler.getPlayer().getUUID());
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> MarktCommands.register(d));
        LOGGER.info("TwoGG Markt geladen");
    }

    private static void handle(ServerPlayer player, String json) {
        Views.Action act;
        try {
            act = Views.GSON.fromJson(json, Views.Action.class);
        } catch (RuntimeException e) {
            return;
        }
        // Pakete kommen vom Client: fehlende Felder dürfen keine Ausnahme im Netzwerk-Handler auslösen
        if (act == null || act.a == null || act.screen == null || !MarktRuntime.running()) return;
        try {
            switch (act.screen) {
                case "market" -> StallService.handle(player, act);
                case "shop" -> ShopService.handle(player, act);
                case "buyer" -> BuyerService.handle(player, act);
                case LadenService.SCREEN -> LadenService.handle(player, act);
                case "board" -> BoardService.handle(player, act);
                case "admin" -> AdminService.handle(player, act);
                case "lottery", "advent" -> ActivityService.handle(player, act);
                default -> { }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Markt-Aktion {} von {} fehlgeschlagen", act.a, player.getGameProfile().name(), e);
        }
    }
}
