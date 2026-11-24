package de.twogg.markt.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Auftragsbrett (seit 0.5.1): Das Modell ist zwei Blöcke hoch, Minecraft prüft Klicks aber nur im eigenen Blockfeld.
 * Darum steht darüber ein unsichtbarer {@link BoardTopBlock}, der Klicks und Umriss der oberen Hälfte übernimmt.
 * Bretter aus älteren Versionen bekommen den Aufsatz beim ersten Rechtsklick nachgerüstet.
 */
public class BoardBlock extends MarktBlock {
    private final Supplier<Block> top;
    private final BiConsumer<ServerPlayer, BlockPos> use;
    private final VoxelShape lower;

    public BoardBlock(Properties properties, BiConsumer<ServerPlayer, BlockPos> onUse, VoxelShape shape, Supplier<Block> top) {
        super(properties, onUse, shape);
        this.top = top;
        this.use = onUse;
        this.lower = shape;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new BoardBlock(p, use, lower, top));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos above = ctx.getClickedPos().above();
        if (above.getY() > ctx.getLevel().getMaxY() || !ctx.getLevel().getBlockState(above).canBeReplaced(ctx)) return null;
        return super.getStateForPlacement(ctx);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        ensureTop(level, pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) ensureTop(level, pos, state);   // ältere Bretter nachrüsten
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** Setzt den unsichtbaren Aufsatz, wenn darüber Platz (Luft) ist und noch keiner steht. */
    public void ensureTop(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        BlockPos above = pos.above();
        BlockState there = level.getBlockState(above);
        if (there.is(top.get())) return;
        if (!there.isAir()) return;
        Direction facing = state.getValue(FACING);
        level.setBlock(above, top.get().defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);
    }
}
