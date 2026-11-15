package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Plot;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Umrisse als Partikel (nur für den jeweiligen Spieler) und der Hinweis beim Betreten eines Stands.
 * Admins mit Marktplaner in der Hand sehen alle Stände (Farbe = Status), Räumzonen und ihre Auswahl.
 * Spieler sehen einen Stand ein paar Sekunden lang, wenn sie ihn betreten, oder alle mit {@code /markt zeigen}.
 */
public final class Outlines {
    private static final int FREE = 0x55E07A, RENTED = 0xF2B13A, OWNED = 0x58A6FF, LOCKED = 0xE5484D, SEL = 0xFFFFFF, ZONE = 0x9AE6FF;
    private static final Map<UUID, String> INSIDE = new HashMap<>();
    /** Spieler → bis wann Umrisse sichtbar sind (ms) und welcher Stand (leer = alle). */
    private static final Map<UUID, Long> SHOW_UNTIL = new HashMap<>();
    private static final Map<UUID, String> SHOW_PLOT = new HashMap<>();

    private Outlines() {}

    public static void show(ServerPlayer p, String plotId, long ms) {
        SHOW_UNTIL.put(p.getUUID(), System.currentTimeMillis() + ms);
        SHOW_PLOT.put(p.getUUID(), plotId == null ? "" : plotId);
    }

    public static void forget(UUID id) {
        INSIDE.remove(id);
        SHOW_UNTIL.remove(id);
        SHOW_PLOT.remove(id);
    }

    public static void tick(MinecraftServer server) {
        long now = MarktRuntime.now();
        MarktData d = MarktRuntime.data();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String dim = p.level().dimension().identifier().toString();
            enterHint(p, dim, now);
            boolean planner = (p.getMainHandItem().is(ModContent.PLANNER) || p.getOffhandItem().is(ModContent.PLANNER)) && AdminService.isAdmin(p);
            Long until = SHOW_UNTIL.get(p.getUUID());
            boolean timed = until != null && until > System.currentTimeMillis();
            if (!planner && !timed) continue;
            String only = planner ? "" : SHOW_PLOT.getOrDefault(p.getUUID(), "");
            for (Plot plot : d.plots.values()) {
                if (!only.isEmpty() && !only.equals(plot.id)) continue;
                if (!near(p, dim, plot.box)) continue;
                int c = switch (plot.status(now)) {
                    case FREE -> FREE;
                    case RENTED -> RENTED;
                    case OWNED -> OWNED;
                    case LOCKED -> LOCKED;
                };
                draw(p, plot.box, c);
            }
            if (planner) {
                for (MarktData.Zone z : d.zones.values()) if (near(p, dim, z.box)) draw(p, z.box, ZONE);
                Selections.Sel s = Selections.get(p);
                if (s != null && s.complete() && dim.equals(s.dim)) draw(p, s.box(), SEL);
                else if (s != null && dim.equals(s.dim)) {
                    var c = s.a != null ? s.a : s.b;
                    if (c != null) draw(p, Box.of(dim, c.getX(), c.getY(), c.getZ(), c.getX(), c.getY(), c.getZ()), SEL);
                }
            }
        }
    }

    private static void enterHint(ServerPlayer p, String dim, long now) {
        Plot plot = MarktRuntime.data().plotAt(dim, p.getBlockX(), p.getBlockY(), p.getBlockZ());
        String id = plot == null ? "" : plot.id;
        String before = INSIDE.put(p.getUUID(), id);
        if (id.isEmpty() || id.equals(before) || !MarktRuntime.config().stalls.enterHint) return;
        String text;
        switch (plot.status(now)) {
            case FREE -> {
                StringBuilder sb = new StringBuilder("Stand „" + plot.name + "“ – frei");
                if (plot.rentable()) sb.append(" · Miete ").append(MarktRuntime.fmt(MarketMath.priceTenths(plot.rentPrice, MarktRuntime.rate())))
                        .append("/").append(MarktRuntime.config().stalls.periodName);
                if (plot.buyable()) sb.append(" · Kauf ").append(MarktRuntime.fmt(MarketMath.priceTenths(plot.buyPrice, MarktRuntime.rate())));
                text = sb.append(" · /markt").toString();
            }
            case LOCKED -> text = "Stand „" + plot.name + "“ – nicht zu vergeben";
            default -> text = "„" + plot.name + "“ – Stand von " + plot.ownerName;
        }
        p.sendOverlayMessage(Component.literal(text).withStyle(plot.active(now) ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        if (!plot.active(now)) show(p, plot.id, 4000);
    }

    private static boolean near(ServerPlayer p, String dim, Box b) {
        if (!b.dim.equals(dim)) return false;
        double dx = Math.max(0, Math.max(b.minX - p.getX(), p.getX() - (b.maxX + 1)));
        double dz = Math.max(0, Math.max(b.minZ - p.getZ(), p.getZ() - (b.maxZ + 1)));
        return dx * dx + dz * dz < 48 * 48;
    }

    /** Die zwölf Kanten des Quaders, ein Partikel pro Block (höchstens ~600). */
    private static void draw(ServerPlayer p, Box b, int color) {
        ServerLevel level = (ServerLevel) p.level();
        DustParticleOptions opt = new DustParticleOptions(color, 1.0f);
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX + 1, y1 = b.maxY + 1, z1 = b.maxZ + 1;
        int step = Math.max(1, (int) ((b.sizeX() + b.sizeY() + b.sizeZ()) * 4 / 600));
        for (double x = x0; x <= x1; x += step) {
            dot(level, p, opt, x, y0, z0); dot(level, p, opt, x, y0, z1);
            dot(level, p, opt, x, y1, z0); dot(level, p, opt, x, y1, z1);
        }
        for (double z = z0 + step; z < z1; z += step) {
            dot(level, p, opt, x0, y0, z); dot(level, p, opt, x1, y0, z);
            dot(level, p, opt, x0, y1, z); dot(level, p, opt, x1, y1, z);
        }
        for (double y = y0 + step; y < y1; y += step) {
            dot(level, p, opt, x0, y, z0); dot(level, p, opt, x1, y, z0);
            dot(level, p, opt, x0, y, z1); dot(level, p, opt, x1, y, z1);
        }
    }

    private static void dot(ServerLevel level, ServerPlayer p, DustParticleOptions opt, double x, double y, double z) {
        level.sendParticles(p, opt, true, false, x, y, z, 1, 0, 0, 0, 0);
    }
}
