package de.twogg.markt.server;

import de.twogg.economy.api.Economy;
import de.twogg.economy.core.TxResult;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.MarktData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Alles Geld läuft über die öffentliche API von TwoGG Economy. Bezahlen vernichtet Geld ({@code BURN} bzw.
 * {@code CASH_BURN}), Auszahlen erzeugt neues ({@code MINT}). Der Markt hält dazwischen Kassen und Treuhand-Beträge
 * selbst – so geht nichts verloren, wenn der Empfänger gerade offline ist.
 */
public final class Pay {
    private Pay() {}

    public static TxResult charge(ServerPlayer p, long tenths, boolean cash, String source, String reason) {
        if (!Economy.available()) return TxResult.fail("Die Bank ist gerade geschlossen.");
        if (tenths <= 0) return TxResult.ok(0, 0, Economy.balance(p));
        if (cash && !MarktRuntime.config().stalls.allowCash) cash = false;
        return cash ? Economy.chargeCash(p, tenths, source, reason) : Economy.charge(p, tenths, source, reason);
    }

    /** Aufs Konto (oder bar). Klappt es nicht (kein aktiver Charakter), wird der Betrag beim nächsten Login gezahlt. */
    public static boolean payout(ServerPlayer p, long tenths, boolean cash, String source, String reason) {
        if (tenths <= 0) return true;
        TxResult r = Economy.available()
                ? (cash ? Economy.giveCash(p, tenths, source, reason) : Economy.pay(p, tenths, source, reason))
                : TxResult.fail("Economy läuft nicht");
        if (!r.ok() && cash && Economy.available()) r = Economy.pay(p, tenths, source, reason);
        if (r.ok()) return true;
        MarktRuntime.data().addPending(p.getUUID().toString(), p.getGameProfile().name(), tenths, reason);
        MarktRuntime.markDirty();
        p.sendSystemMessage(Component.literal(MarktRuntime.fmt(tenths) + " vorgemerkt (" + r.message()
                + "). Du bekommst es beim nächsten Login.").withStyle(ChatFormatting.GOLD));
        return false;
    }

    /** Für Offline-Empfänger: vormerken und beim nächsten Login zahlen. */
    public static void later(String uuid, String name, long tenths, String reason) {
        MarktRuntime.data().addPending(uuid, name, tenths, reason);
        MarktRuntime.markDirty();
    }

    /** Vorgemerkte Beträge auszahlen (Login, Fenster öffnen). */
    public static void settle(ServerPlayer p) {
        if (!Economy.available() || !Economy.hasAccount(p)) return;
        MarktData.Pending pend = MarktRuntime.data().pending.get(p.getUUID().toString());
        if (pend == null || pend.amount <= 0) return;
        String reason = pend.reasons.size() == 1 ? pend.reasons.getFirst() : "Markt: Gutschriften";
        TxResult r = Economy.pay(p, pend.amount, "markt:gutschrift", reason);
        if (!r.ok()) return;
        MarktRuntime.data().pending.remove(p.getUUID().toString());
        MarktRuntime.markDirty();
        p.sendSystemMessage(Component.literal("Markt: " + MarktRuntime.fmt(pend.amount) + " gutgeschrieben – "
                + String.join(", ", pend.reasons)).withStyle(ChatFormatting.GOLD));
    }
}
