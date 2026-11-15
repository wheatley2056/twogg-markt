package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.Plot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Auswahl mit dem Marktplaner: zwei Ecken pro Admin, nur im Speicher. */
public final class Selections {
    public static final class Sel {
        public String dim;
        public BlockPos a, b;

        public boolean complete() {
            return a != null && b != null;
        }

        public Box box() {
            return complete() ? Box.of(dim, a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ()) : null;
        }
    }

    private static final Map<UUID, Sel> SEL = new HashMap<>();

    private Selections() {}

    public static Sel get(ServerPlayer p) {
        return SEL.get(p.getUUID());
    }

    public static void clear(ServerPlayer p) {
        SEL.remove(p.getUUID());
    }

    public static void corner(ServerPlayer p, BlockPos pos, int which) {
        if (!AdminService.isAdmin(p)) {
            p.sendOverlayMessage(Component.literal("Den Marktplaner können nur Admins benutzen.").withStyle(ChatFormatting.RED));
            return;
        }
        String dim = p.level().dimension().identifier().toString();
        Sel s = SEL.computeIfAbsent(p.getUUID(), k -> new Sel());
        if (s.dim != null && !s.dim.equals(dim)) {
            s.a = null;
            s.b = null;
        }
        s.dim = dim;
        if (which == 1) s.a = pos.immutable();
        else s.b = pos.immutable();
        String msg = "Ecke " + which + ": " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        Box box = s.box();
        if (box != null) {
            msg += " · Auswahl " + box.size();
            for (Plot o : MarktRuntime.data().plots.values()) {
                if (o.box.intersects(box)) {
                    msg += " · überschneidet „" + o.name + "“";
                    break;
                }
            }
        }
        p.sendOverlayMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA));
    }

    /** Auswahl nach oben/unten erweitern (Stände brauchen meist Platz über dem Boden). */
    public static void expand(ServerPlayer p, int up, int down) {
        Sel s = get(p);
        if (s == null || !s.complete()) return;
        int lo = Math.min(s.a.getY(), s.b.getY()) - down, hi = Math.max(s.a.getY(), s.b.getY()) + up;
        s.a = new BlockPos(s.a.getX(), lo, s.a.getZ());
        s.b = new BlockPos(s.b.getX(), hi, s.b.getZ());
    }
}
