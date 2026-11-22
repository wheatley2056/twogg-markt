package de.twogg.markt.content.guide;

import de.twogg.markt.MarktRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Markt-Handbuch beim ersten Betreten verteilen (einmal pro Spieler, abschaltbar: {@code guide.giveOnJoin}). */
public final class GuideService {

    private GuideService() {
    }

    public static void join(ServerPlayer p) {
        if (!MarktRuntime.config().guide.giveOnJoin) {
            return;
        }
        String id = p.getUUID().toString();
        if (!MarktRuntime.data().guideGiven.add(id)) {
            return;
        }
        MarktRuntime.markDirty();
        GuideBook.give(p);
        p.sendSystemMessage(Component.literal("Willkommen! Das ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Markt-Handbuch").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" liegt in deinem Inventar – Rechtsklick zum Lesen, jederzeit neu mit /markt handbuch.")
                        .withStyle(ChatFormatting.GRAY)));
    }
}
