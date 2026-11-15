package de.twogg.markt.client;

import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.net.Payloads;
import de.twogg.kit.KitApps;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;

public class TwoggMarktClient implements ClientModInitializer {
    /** Weihnachtsrot in der Toolbox. */
    private static final int ACCENT = 0xFFEE5A4F;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.View.TYPE, (p, ctx) -> MarktScreen.receive(p.json()));
        // Kachel in der TwoGG Toolbox: öffnet die Marktverwaltung (der Server prüft die Rechte)
        KitApps.register(new KitApps.App(TwoggMarkt.MODID, "Markt", ACCENT, false, AdminScreen::new,
                Identifier.fromNamespaceAndPath(TwoggMarkt.MODID, "icon.png"), true));
    }
}
