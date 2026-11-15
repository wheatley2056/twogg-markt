package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Räumdienst: In Räumzonen (Gassen und Plätze des Markts) zahlt der Markt fürs Schneeschippen. Gezählt werden
 * Schneeschichten und Schneeblöcke im Überlebensmodus. Selbst gesetzter Schnee zählt nicht.
 */
public final class SnowService {
    /** Von Spielern gesetzter Schnee in Räumzonen (nur im Speicher; nach einem Neustart zählt er wieder). */
    private static final Set<GlobalPos> PLACED = new HashSet<>();

    private SnowService() {}

    public static boolean isSnow(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK);
    }

    /** Vor dem Setzen: Schnee aus der Hand in einer Räumzone merken. */
    public static void notePlacement(ServerPlayer p, ItemStack hand, BlockPos clicked, BlockPos relative) {
        if (!(hand.is(Items.SNOW) || hand.is(Items.SNOW_BLOCK))) return;
        String dim = p.level().dimension().identifier().toString();
        for (BlockPos pos : new BlockPos[]{clicked, relative}) {
            if (MarktRuntime.data().zoneAt(dim, pos.getX(), pos.getY(), pos.getZ()) != null) {
                if (PLACED.size() > 50_000) PLACED.clear();
                PLACED.add(GlobalPos.of(p.level().dimension(), pos.immutable()));
            }
        }
    }

    public static void broken(Level level, ServerPlayer p, BlockPos pos, BlockState state) {
        MarktConfig.Snow cfg = MarktRuntime.config().snow;
        if (!cfg.enabled || !isSnow(state) || p.isCreative() || p.isSpectator()) return;
        String dim = level.dimension().identifier().toString();
        MarktData.Zone zone = MarktRuntime.data().zoneAt(dim, pos.getX(), pos.getY(), pos.getZ());
        if (zone == null) return;
        if (PLACED.remove(GlobalPos.of(level.dimension(), pos.immutable()))) return;
        MarktData.Daily d = MarktRuntime.data().daily(p.getUUID().toString());
        long pay = MarketMath.tenths(cfg.pay), max = MarketMath.tenths(cfg.dailyMax);
        if (pay <= 0 || d.snowPaid + pay > max) {
            if (d.snowBlocks % 32 == 0) {
                p.sendOverlayMessage(Component.literal("Räumdienst: Für heute ist dein Lohn voll. Danke!").withStyle(ChatFormatting.GRAY));
            }
            d.snowBlocks++;
            return;
        }
        d.snowBlocks++;
        MarktRuntime.markDirty();
        if (d.snowBlocks % cfg.blocksPerPay != 0) {
            p.sendOverlayMessage(Component.literal("Räumdienst " + zone.name + ": " + (d.snowBlocks % cfg.blocksPerPay) + "/"
                    + cfg.blocksPerPay).withStyle(ChatFormatting.AQUA));
            return;
        }
        d.snowPaid += pay;
        Pay.payout(p, pay, false, "markt:raeumdienst", "Räumdienst " + zone.name);
        p.sendOverlayMessage(Component.literal("Räumdienst: +" + MarktRuntime.fmt(pay) + " (heute " + MarktRuntime.fmt(d.snowPaid)
                + " von " + MarktRuntime.fmt(max) + ")").withStyle(ChatFormatting.GOLD));
    }
}
