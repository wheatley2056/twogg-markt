package de.twogg.markt.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Gemeinsamer Block für Verkaufstheke, Standschild, Ankauf und Auftragsbrett: drehbar, Rechtsklick öffnet ein Fenster
 * auf dem Server ({@code onUse}). Was der Block kann, legt {@link ModContent} fest.
 */
public class MarktBlock extends HorizontalDirectionalBlock {
    private final BiConsumer<ServerPlayer, BlockPos> onUse;
    private final VoxelShape shape;
    /** Form je Blickrichtung (die Form ist für NORTH angegeben, gedreht wie die Blockstates). */
    private final Map<Direction, VoxelShape> shapes;

    public MarktBlock(Properties properties, BiConsumer<ServerPlayer, BlockPos> onUse, VoxelShape shape) {
        super(properties);
        this.onUse = onUse;
        this.shape = shape == null ? Shapes.block() : shape;
        this.shapes = Shapes.rotateHorizontal(this.shape);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new MarktBlock(p, onUse, shape));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes.getOrDefault(state.getValue(FACING), shape);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) onUse.accept(sp, pos);
        return InteractionResult.SUCCESS;
    }

    /** Öffnet das Fenster dieses Blocks für {@code player} an {@code pos} (wie ein Rechtsklick). */
    void open(ServerPlayer player, BlockPos pos) {
        onUse.accept(player, pos);
    }
}
