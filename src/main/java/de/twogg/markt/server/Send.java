package de.twogg.markt.server;

import de.twogg.economy.api.Economy;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.net.Payloads;
import de.twogg.markt.net.Views;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Gemeinsamer Kopf aller Ansichten und das Verschicken. */
public final class Send {
    private Send() {}

    /** Hat der Client die Mod? Sonst eine Meldung im Chat. */
    public static boolean canOpen(ServerPlayer p) {
        if (!MarktRuntime.running()) return false;
        if (!ServerPlayNetworking.canSend(p, Payloads.View.TYPE)) {
            p.sendSystemMessage(Component.literal("Für den Markt brauchst du die Mod TwoGG Markt.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    public static Views.View base(ServerPlayer p, String screen, String message, boolean error) {
        MarktConfig c = MarktRuntime.config();
        Views.View v = new Views.View();
        v.screen = screen;
        v.title = c.title;
        v.subtitle = c.subtitle;
        v.accent = MarktRuntime.accent();
        v.unit = MarktRuntime.unit();
        v.rate = MarktRuntime.rate();
        v.allowCash = c.stalls.allowCash;
        v.periodName = c.stalls.periodName;
        v.periodNamePlural = c.stalls.periodNamePlural;
        v.message = message == null ? "" : message;
        Sessions.Session s = Sessions.get(p);
        v.session = s == null ? 0 : s.id();
        v.error = error;
        if (Economy.available()) {
            v.hasAccount = Economy.hasAccount(p);
            v.balance = v.hasAccount ? Economy.balance(p) : 0;
            v.cash = Economy.cash(p);
        }
        var pend = MarktRuntime.data().pending.get(p.getUUID().toString());
        v.pending = pend == null ? 0 : pend.amount;
        return v;
    }

    public static void send(ServerPlayer p, Views.View v) {
        if (ServerPlayNetworking.canSend(p, Payloads.View.TYPE)) {
            ServerPlayNetworking.send(p, new Payloads.View(Views.GSON.toJson(v)));
        }
    }
}
