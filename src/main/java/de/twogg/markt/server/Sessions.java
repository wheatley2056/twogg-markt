package de.twogg.markt.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wer hat welches Markt-Fenster offen, und an welchem Block? Aktionen aus Block-Fenstern gelten nur, solange der
 * Spieler noch höchstens 8 Blöcke entfernt steht und der Block noch da ist.
 */
public final class Sessions {
    private static final double RANGE_SQ = 8 * 8;
    private static final Map<UUID, Session> OPEN = new HashMap<>();
    private static final Map<UUID, Long> LAST_VIEW = new HashMap<>();

    public record Session(String screen, GlobalPos pos, Block block, String plot, int id) {}

    private static int nextId = 1;

    private Sessions() {}

    public static void open(ServerPlayer p, String screen, BlockPos pos, Block block, String plot) {
        OPEN.put(p.getUUID(), new Session(screen, pos == null ? null : new GlobalPos(p.level().dimension(), pos.immutable()), block, plot, nextId++));
    }

    public static Session get(ServerPlayer p) {
        return OPEN.get(p.getUUID());
    }

    /**
     * Fenster geschlossen. Nur die Sitzung dieses Fenstertyps: Ein spätes „close“ eines alten Fensters darf die Sitzung
     * eines inzwischen geöffneten anderen Fensters nicht beenden – auch nicht eines neuen Fensters desselben Typs:
     * Der Client schickt die Sitzungsnummer mit, die er mit der Ansicht bekommen hat (0 = alter Client, nur Typ prüfen).
     */
    public static void close(ServerPlayer p, String screen, int session) {
        Session s = OPEN.get(p.getUUID());
        if (s != null && s.screen().equals(screen) && (session == 0 || session == s.id())) OPEN.remove(p.getUUID());
    }

    public static void forget(UUID id) {
        OPEN.remove(id);
        LAST_VIEW.remove(id);
    }

    public static Map<UUID, Session> all() {
        return OPEN;
    }

    /** Gültige Sitzung für dieses Fenster, sonst null (und die Sitzung ist weg). */
    public static Session check(ServerPlayer p, String screen) {
        Session s = OPEN.get(p.getUUID());
        if (s == null || !s.screen().equals(screen)) return null;
        if (s.pos() == null) return s;
        BlockPos pos = s.pos().pos();
        if (!p.level().dimension().equals(s.pos().dimension())
                || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > RANGE_SQ
                || (s.block() != null && !p.level().getBlockState(pos).is(s.block()))) {
            OPEN.remove(p.getUUID());
            return null;
        }
        return s;
    }

    /** Ansichten ohne Aktion höchstens alle 200 ms (Paketflut). */
    public static boolean tooSoon(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = LAST_VIEW.get(p.getUUID());
        if (last != null && now - last >= 0 && now - last < 200) return true;
        LAST_VIEW.put(p.getUUID(), now);
        return false;
    }
}
