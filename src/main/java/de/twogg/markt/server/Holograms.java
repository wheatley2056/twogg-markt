package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.Plot;
import de.twogg.markt.mixin.DisplayAccessor;
import de.twogg.markt.mixin.TextDisplayAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Schwebende Schrift über den Ständen und die Bestenliste-Tafel: Vanilla-{@link Display.TextDisplay}s, die der Server
 * setzt und aktuell hält. Jeder Spieler sieht sie, auch ohne Mod. Getaggt mit {@value #TAG}; verwaiste Hologramme
 * (Stand gelöscht, Daten zurückgesetzt) werden beim Laden entfernt.
 */
public final class Holograms {
    public static final String TAG = "twogg_markt_holo";
    /** Zuletzt gesetzter Text pro Hologramm (spart Updates). */
    private static final Map<UUID, String> LAST = new HashMap<>();
    /** Gerade gesetzt, aber noch keinem Stand zugeordnet (sonst entfernt sie der Lade-Hook sofort wieder). */
    private static final Set<String> SPAWNING = new HashSet<>();

    private Holograms() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!MarktRuntime.running() || !entity.entityTags().contains(TAG)) return;
            if (!known().contains(entity.getUUID().toString())) entity.discard();
        });
    }

    private static Set<String> known() {
        Set<String> s = new HashSet<>(SPAWNING);
        for (Plot p : MarktRuntime.data().plots.values()) if (!p.hologram.isEmpty()) s.add(p.hologram);
        if (!MarktRuntime.data().leaderboardHolo.isEmpty()) s.add(MarktRuntime.data().leaderboardHolo);
        return s;
    }

    /** Alle 100 Ticks: Hologramme anlegen, verschieben, Text erneuern. */
    public static void tick() {
        MarktData d = MarktRuntime.data();
        boolean on = MarktRuntime.config().stalls.holograms;
        long now = MarktRuntime.now();
        for (Plot p : d.plots.values()) {
            if (!on) {
                remove(p.box.dim, p.hologram);
                p.hologram = "";
                continue;
            }
            Box b = p.box;
            double y = b.maxY + 1 + MarktRuntime.config().stalls.hologramOffset;
            String id = ensure(b.dim, p.hologram, b.centerX(), y, b.centerZ(), 220);
            if (!id.equals(p.hologram)) {
                p.hologram = id;
                MarktRuntime.markDirty();
            }
            setText(b.dim, id, () -> plotText(p, now));
        }
        if (d.leaderboard != null) {
            Box b = d.leaderboard;
            String id = ensure(b.dim, d.leaderboardHolo, b.minX + 0.5, b.minY + 0.5, b.minZ + 0.5, 260);
            if (!id.equals(d.leaderboardHolo)) {
                d.leaderboardHolo = id;
                MarktRuntime.markDirty();
            }
            setText(b.dim, id, EventService::leaderboardText);
        }
    }

    public static void removePlot(Plot p) {
        remove(p.box.dim, p.hologram);
        p.hologram = "";
    }

    public static void removeLeaderboard() {
        MarktData d = MarktRuntime.data();
        if (d.leaderboard != null) remove(d.leaderboard.dim, d.leaderboardHolo);
        d.leaderboard = null;
        d.leaderboardHolo = "";
    }

    /** Hologramm sicherstellen; gibt die (evtl. neue) UUID zurück, oder die alte, solange der Chunk nicht geladen ist. */
    private static String ensure(String dim, String uuid, double x, double y, double z, int lineWidth) {
        ServerLevel level = Levels.of(dim);
        if (level == null || !level.hasChunkAt(BlockPos.containing(x, y, z))) return uuid;
        Entity e = uuid.isEmpty() ? null : level.getEntity(UUID.fromString(uuid));
        if (e instanceof Display.TextDisplay td) {
            if (td.distanceToSqr(x, y, z) > 0.01) td.snapTo(x, y, z);
            return uuid;
        }
        Display.TextDisplay td = EntityType.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (td == null) return uuid;
        td.snapTo(x, y, z);
        td.addTag(TAG);
        ((DisplayAccessor) td).markt$setBillboard(Display.BillboardConstraints.CENTER);
        ((DisplayAccessor) td).markt$setViewRange(0.6f);
        ((TextDisplayAccessor) td).markt$setLineWidth(lineWidth);
        ((TextDisplayAccessor) td).markt$setBackground(0x90101820);
        String id = td.getUUID().toString();
        SPAWNING.add(id);
        try {
            level.addFreshEntity(td);
        } finally {
            SPAWNING.remove(id);
        }
        return id;
    }

    private static void setText(String dim, String uuid, Supplier<Component> text) {
        if (uuid.isEmpty()) return;
        ServerLevel level = Levels.of(dim);
        if (level == null) return;
        Entity e = level.getEntity(UUID.fromString(uuid));
        if (!(e instanceof Display.TextDisplay td)) return;
        Component c = text.get();
        String key = c.getString() + "|" + c.hashCode();
        if (key.equals(LAST.get(td.getUUID()))) return;
        LAST.put(td.getUUID(), key);
        ((TextDisplayAccessor) td).markt$setText(c);
    }

    private static void remove(String dim, String uuid) {
        if (uuid == null || uuid.isEmpty()) return;
        ServerLevel level = Levels.of(dim);
        Entity e = level == null ? null : level.getEntity(UUID.fromString(uuid));
        if (e != null) e.discard();
        LAST.remove(UUID.fromString(uuid));
    }

    /** Text über einem Stand. */
    static Component plotText(Plot p, long now) {
        MarktConfig c = MarktRuntime.config();
        MutableComponent t = Component.literal(p.name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        switch (p.status(now)) {
            case FREE -> {
                t.append(line("frei", ChatFormatting.GREEN));
                StringBuilder sb = new StringBuilder();
                if (p.rentable()) sb.append(MarktRuntime.fmt(MarketMath.priceTenths(p.rentPrice, MarktRuntime.rate()))).append(" pro ").append(c.stalls.periodName);
                if (p.buyable()) sb.append(sb.isEmpty() ? "" : " · ").append("Kauf ").append(MarktRuntime.fmt(MarketMath.priceTenths(p.buyPrice, MarktRuntime.rate())));
                t.append(line(sb.toString(), ChatFormatting.WHITE));
                t.append(line("Rechtsklick aufs Standschild oder /markt", ChatFormatting.GRAY));
            }
            case LOCKED -> t.append(line("nicht zu vergeben", ChatFormatting.RED));
            default -> {
                t.append(line("Stand von " + p.ownerName, ChatFormatting.WHITE));
                if (!p.note.isEmpty()) t.append(line(p.note, ChatFormatting.GRAY));
                t.append(line(p.offers.isEmpty() ? "noch keine Ware" : p.offers.size() + (p.offers.size() == 1 ? " Angebot" : " Angebote")
                        + (p.shopOpen ? " · geöffnet" : " · geschlossen"), p.shopOpen ? ChatFormatting.AQUA : ChatFormatting.RED));
            }
        }
        var ev = MarktRuntime.data().event;
        if (ev.running() && c.event.vote && p.active(now)) {
            long votes = ev.voteCount().getOrDefault(p.id, 0L);
            t.append(line("★ " + votes + (votes == 1 ? " Stimme" : " Stimmen") + " für den schönsten Stand", ChatFormatting.YELLOW));
        }
        return t;
    }

    static MutableComponent line(String s, ChatFormatting... style) {
        return Component.literal("\n" + s).withStyle(style);
    }
}
