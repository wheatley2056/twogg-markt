package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.MarktBlock;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.Plot;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bauschutz in Ständen: Bauen, Abbauen, Kisten öffnen und Deko anfassen dürfen nur Besitzer, Helfer und Admins.
 * Freie Stände sind (einstellbar) ebenfalls geschützt. Die Markt-Blöcke (Theke, Schild …) darf jeder benutzen.
 * Außerhalb der Stände greift der Markt nicht ein.
 */
public final class Protection {
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private Protection() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> {
            if (!(player instanceof ServerPlayer sp)) return true;
            if (!canBuild(sp, level, pos)) {
                deny(sp, pos);
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
            if (player instanceof ServerPlayer sp) SnowService.broken(level, sp, pos, state);
        });
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (sp.getItemInHand(hand).is(ModContent.PLANNER)) {
                Selections.corner(sp, pos, 1);
                return InteractionResult.FAIL;
            }
            if (!canBuild(sp, level, pos)) {
                deny(sp, pos);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            ItemStack stack = sp.getItemInHand(hand);
            if (level.getBlockState(pos).getBlock() instanceof MarktBlock && !sp.isSecondaryUseActive()) return InteractionResult.PASS;
            BlockPos target = pos.relative(hit.getDirection());
            if (!canBuild(sp, level, pos) || (!stack.isEmpty() && !canBuild(sp, level, target))) {
                deny(sp, pos);
                return InteractionResult.FAIL;
            }
            if (!stack.isEmpty()) {
                SnowService.notePlacement(sp, stack, pos, target);
                // Die Theke gehört in den eigenen Stand
                if (stack.is(ModContent.COUNTER_ITEM) && !AdminService.isAdmin(sp)) {
                    Plot plot = plotAt(level, target);
                    if (plot == null || !plot.member(sp.getUUID().toString(), MarktRuntime.now())) {
                        hint(sp, "Die Verkaufstheke kannst du nur in deinen eigenen Stand stellen.");
                        return InteractionResult.FAIL;
                    }
                }
            }
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            ItemStack stack = sp.getItemInHand(hand);
            if (!(stack.getItem() instanceof BucketItem)) return InteractionResult.PASS;
            HitResult hr = sp.pick(sp.blockInteractionRange(), 0, true);
            if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
                if (!canBuild(sp, level, bh.getBlockPos()) || !canBuild(sp, level, bh.getBlockPos().relative(bh.getDirection()))) {
                    deny(sp, bh.getBlockPos());
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entityCheck(player, level, entity));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entityCheck(player, level, entity));
    }

    private static InteractionResult entityCheck(Player player, Level level, Entity entity) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // Lebewesen (Tiere, Monster, Spieler) bleiben frei; geschützt sind Rahmen, Rüstungsständer, Bilder, Loren …
        if (entity instanceof LivingEntity && !(entity instanceof ArmorStand)) return InteractionResult.PASS;
        if (!canBuild(sp, level, entity.blockPosition())) {
            deny(sp, entity.blockPosition());
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }

    static Plot plotAt(Level level, BlockPos pos) {
        return MarktRuntime.data().plotAt(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean canBuild(ServerPlayer p, Level level, BlockPos pos) {
        if (!MarktRuntime.running()) return true;
        Plot plot = plotAt(level, pos);
        if (plot == null) return true;
        if (AdminService.isAdmin(p)) return true;
        long now = MarktRuntime.now();
        if (plot.member(p.getUUID().toString(), now)) return true;
        return plot.active(now) ? false : !MarktRuntime.config().stalls.protectFree;
    }

    private static void deny(ServerPlayer p, BlockPos pos) {
        Plot plot = plotAt(p.level(), pos);
        if (plot == null) return;
        long now = MarktRuntime.now();
        hint(p, plot.active(now) ? "Das ist der Stand von " + plot.ownerName + "." : "Der Stand „" + plot.name + "“ ist frei – mieten mit /markt.");
    }

    private static void hint(ServerPlayer p, String text) {
        long now = System.currentTimeMillis();
        Long last = LAST_HINT.get(p.getUUID());
        if (last != null && now - last < 1500) return;
        LAST_HINT.put(p.getUUID(), now);
        p.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }

    public static void forget(UUID id) {
        LAST_HINT.remove(id);
    }
}
