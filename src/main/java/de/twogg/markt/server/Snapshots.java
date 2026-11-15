package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.Plot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leerzustand eines Stands: Beim Einzeichnen werden die Blöcke gesichert ({@code <welt>/twogg-markt/snapshots/<id>.nbt}),
 * beim Zurücksetzen wieder hergestellt. Kisteninhalte und Block-Daten (Schilder-Texte) werden nicht gesichert.
 */
public final class Snapshots {
    private Snapshots() {}

    private static Path file(String id) {
        return MarktRuntime.dir().resolve("snapshots").resolve(id + ".nbt");
    }

    public static boolean exists(String id) {
        return MarktRuntime.running() && Files.exists(file(id));
    }

    public static void delete(String id) {
        try {
            Files.deleteIfExists(file(id));
        } catch (IOException ignored) {
        }
    }

    private static ServerLevel level(Box b) {
        Identifier id = Identifier.tryParse(b.dim);
        if (id == null) return null;
        return MarktRuntime.server().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    public static boolean take(Plot plot) {
        ServerLevel level = level(plot.box);
        if (level == null) return false;
        Box b = plot.box;
        Map<BlockState, Integer> index = new HashMap<>();
        ListTag palette = new ListTag();
        int[] data = new int[(int) b.volume()];
        int i = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = b.minY; y <= b.maxY; y++)
            for (int z = b.minZ; z <= b.maxZ; z++)
                for (int x = b.minX; x <= b.maxX; x++) {
                    BlockState s = level.getBlockState(pos.set(x, y, z));
                    Integer n = index.get(s);
                    if (n == null) {
                        n = palette.size();
                        index.put(s, n);
                        palette.add(NbtUtils.writeBlockState(s));
                    }
                    data[i++] = n;
                }
        CompoundTag tag = new CompoundTag();
        tag.putString("box", b.toString());
        tag.put("palette", palette);
        tag.putIntArray("blocks", data);
        try {
            Files.createDirectories(file(plot.id).getParent());
            NbtIo.writeCompressed(tag, file(plot.id));
            return true;
        } catch (IOException e) {
            TwoggMarkt.LOGGER.error("Leerzustand von {} nicht gespeichert: {}", plot.id, e.toString());
            return false;
        }
    }

    /** Stand auf den gesicherten Zustand setzen. Kisten werden vorher geleert (nichts fällt heraus). */
    public static boolean restore(Plot plot) {
        ServerLevel level = level(plot.box);
        if (level == null || !exists(plot.id)) return false;
        try {
            CompoundTag tag = NbtIo.readCompressed(file(plot.id), NbtAccounter.unlimitedHeap());
            if (!plot.box.toString().equals(tag.getStringOr("box", ""))) {
                TwoggMarkt.LOGGER.warn("Leerzustand von {} passt nicht mehr zum Bereich – nicht zurückgesetzt.", plot.id);
                return false;
            }
            var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
            ListTag pal = tag.getListOrEmpty("palette");
            List<BlockState> palette = new ArrayList<>();
            for (int i = 0; i < pal.size(); i++) palette.add(NbtUtils.readBlockState(blocks, pal.getCompoundOrEmpty(i)));
            int[] data = tag.getIntArray("blocks").orElse(new int[0]);
            Box b = plot.box;
            if (data.length != b.volume()) return false;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int i = 0;
            for (int y = b.minY; y <= b.maxY; y++)
                for (int z = b.minZ; z <= b.maxZ; z++)
                    for (int x = b.minX; x <= b.maxX; x++) {
                        BlockState want = data[i] >= 0 && data[i] < palette.size() ? palette.get(data[i]) : Blocks.AIR.defaultBlockState();
                        i++;
                        pos.set(x, y, z);
                        BlockState have = level.getBlockState(pos);
                        if (have == want) continue;
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be instanceof Clearable c) c.clearContent();
                        level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
            return true;
        } catch (IOException | RuntimeException e) {
            TwoggMarkt.LOGGER.error("Stand {} nicht zurückgesetzt: {}", plot.id, e.toString());
            return false;
        }
    }
}
