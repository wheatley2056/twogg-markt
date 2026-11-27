package de.twogg.markt.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Unsichtbare obere Hälfte des Auftragsbretts (seit 0.5.1): Umriss und Klicks für den Teil des Modells über dem Block.
 * Rechtsklick öffnet das Brett darunter; fehlt das Brett, verschwindet der Aufsatz. Abbauen bricht beide.
 */
public class BoardTopBlock extends MarktBlock {
    private final Supplier<Block> board;
    private final net.minecraft.world.phys.shapes.VoxelShape upper;

    public BoardTopBlock(Properties properties, net.minecraft.world.phys.shapes.VoxelShape shape, Supplier<Block> board) {
        super(properties, opener(board), shape);
        this.board = board;
        this.upper = shape;
    }

    /** Rechtsklick oben = Rechtsklick auf das Brett darunter (gleiche Prüfung, gleiche Position). */
    private static BiConsumer<ServerPlayer, BlockPos> opener(Supplier<Block> board) {
        return (player, pos) -> {
            BlockPos below = pos.below();
            BlockState st = player.level().getBlockState(below);
            if (st.is(board.get()) && st.getBlock() instanceof MarktBlock mb) mb.open(player, below);
        };
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new BoardTopBlock(p, upper, board));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;   // gezeichnet wird das ganze Modell vom Brett darunter
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighborPos, BlockState neighbor, RandomSource random) {
        if (dir == Direction.DOWN && !neighbor.is(board.get())) return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(board.get());
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos below = pos.below();
        if (!level.isClientSide() && level.getBlockState(below).is(board.get())) {
            level.destroyBlock(below, !player.isCreative(), player);   // Brett mit abbauen (droppt dort)
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(board.get());
    }
}
