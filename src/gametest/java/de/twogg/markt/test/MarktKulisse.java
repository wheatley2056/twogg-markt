package de.twogg.markt.test;

import de.twogg.markt.content.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Kulisse für die Spieltest-Bilder (Markt-Handbuch): eine kleine Weihnachtsmarkt-Gasse im Schnee – Buden aus Fichte
 * mit verschneiten Dächern und rot-weißen Blenden, Laternen, Tannen, ein Hüttchen mit dem Adventskalender – und ein
 * Schauraum mit allen Markt-Blöcken in verschiedenen Ausrichtungen (Abnahme der Modelle).
 *
 * <p>Alles wird mit {@link Block#UPDATE_CLIENTS} gesetzt (keine Nachbar-Updates), damit Schnee auf Treppen und
 * hängende Laternen stehen bleiben. Koordinaten relativ zu {@code base} (Standort des Spielers, Blick nach Süden).
 */
final class MarktKulisse {
    private MarktKulisse() {}

    /** Positionen der Markt-Blöcke in der Gasse (relativ zur Basis). */
    static final BlockPos BUYER = new BlockPos(-6, 0, 4), BOARD = new BlockPos(-3, 0, 1), LOTTERY = new BlockPos(3, 0, 1),
            GIFT = new BlockPos(-10, 0, 0), ADVENT = new BlockPos(11, 1, 2), DEKO_THEKE = new BlockPos(6, 0, 4);

    private static void set(ServerLevel l, BlockPos p, BlockState s) {
        l.setBlock(p, s, Block.UPDATE_CLIENTS);
    }

    private static BlockState facing(Block b, Direction d) {
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
    }

    /** Gasse: Boden, Buden, Hütte, Laternen und Tannen. Die Stände 1 (−2…2) und 2 (4…8) liegen bei z 3…7. */
    static void gasse(ServerLevel l, BlockPos base) {
        // Freiräumen und Boden: Schnee, in der Gasse Pflaster mit etwas Schnee
        for (int x = -18; x <= 18; x++)
            for (int z = -12; z <= 22; z++) {
                for (int y = 0; y <= 12; y++) set(l, base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                boolean gasse = z >= -3 && z <= 1;
                BlockState boden = Blocks.SNOW_BLOCK.defaultBlockState();
                if (gasse) {
                    int h = Math.floorMod(x * 7 + z * 13 + (x * z) % 3, 7);
                    boden = h <= 1 ? Blocks.STONE_BRICKS.defaultBlockState()
                            : h == 2 ? Blocks.ANDESITE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
                }
                set(l, base.offset(x, -1, z), boden);
            }
        // Buden: Ankauf (−8…−4), Stand 1 (−2…2), Stand 2 (4…8), Hütte mit Adventskalender (10…14)
        bude(l, base, -8, -4, 3, 7, true);
        bude(l, base, -2, 2, 3, 7, true);
        bude(l, base, 4, 8, 3, 7, true);
        bude(l, base, 10, 14, 3, 7, false);
        set(l, base.offset(13, 0, 3), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(l, base.offset(13, 1, 3), Blocks.SPRUCE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // Einrichtung: Fässer, Heu, Kisten
        set(l, base.offset(-7, 0, 6), Blocks.BARREL.defaultBlockState());
        set(l, base.offset(-5, 0, 6), Blocks.HAY_BLOCK.defaultBlockState());
        set(l, base.offset(-1, 0, 6), Blocks.BARREL.defaultBlockState());
        set(l, base.offset(7, 0, 6), Blocks.BARREL.defaultBlockState());
        set(l, base.offset(5, 0, 6), Blocks.BARREL.defaultBlockState());
        set(l, base.offset(BUYER), facing(ModContent.BUYER, Direction.NORTH));
        set(l, base.offset(DEKO_THEKE), facing(ModContent.COUNTER, Direction.NORTH));
        // Laternen an Zaunpfählen entlang der Gasse
        for (int x : new int[]{-15, -9, 9, 16}) laterne(l, base.offset(x, 0, 1));
        // Tannen: große hinter den Buden, kleine am Rand
        tanne(l, base.offset(-6, 0, 14), 9, 3, true);
        tanne(l, base.offset(-13, 0, 11), 6, 2, false);
        tanne(l, base.offset(9, 0, 11), 7, 2, false);
        tanne(l, base.offset(-12, 0, 3), 5, 2, false);
        tanne(l, base.offset(-14, 0, -3), 4, 1, false);
        tanne(l, base.offset(-11, 0, 1), 3, 1, false);
        tanne(l, base.offset(16, 0, 4), 6, 2, false);
        tanne(l, base.offset(15, 0, -4), 4, 1, false);
        // Markt-Blöcke der Gasse
        set(l, base.offset(BOARD), facing(ModContent.BOARD, Direction.NORTH));
        set(l, base.offset(LOTTERY), facing(ModContent.LOTTERY, Direction.NORTH));
        set(l, base.offset(GIFT), facing(ModContent.GIFT, Direction.EAST));
        set(l, base.offset(ADVENT), facing(ModContent.ADVENT, Direction.NORTH));
    }

    /**
     * Bude aus Fichte: Eckpfosten, Wände, offene Front mit rot-weißer Blende (oder geschlossen), Dach mit Schnee.
     * Das Dach liegt auf Höhe 3 – die Oberkante des Stands (Höhe 4) bleibt frei, so sieht man die Umrisse des Planers.
     */
    static void bude(ServerLevel l, BlockPos base, int x0, int x1, int z0, int z1, boolean offen) {
        BlockState planks = Blocks.SPRUCE_PLANKS.defaultBlockState(), log = Blocks.SPRUCE_LOG.defaultBlockState();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) set(l, base.offset(x, -1, z), planks);
        for (int y = 0; y <= 2; y++) {
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) set(l, base.offset(c[0], y, c[1]), log);
            for (int x = x0 + 1; x < x1; x++) {
                set(l, base.offset(x, y, z1), planks);
                if (!offen) set(l, base.offset(x, y, z0), planks);
            }
            for (int z = z0 + 1; z < z1; z++) {
                set(l, base.offset(x0, y, z), planks);
                set(l, base.offset(x1, y, z), planks);
            }
        }
        if (offen) for (int x = x0 + 1; x < x1; x++)
            set(l, base.offset(x, 2, z0), ((x - x0) % 2 == 1 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL).defaultBlockState());
        // Dach: vorn und hinten Treppen, dazwischen Bretter mit Schnee (seitlich ohne Überstand – Lücken zwischen Buden)
        for (int x = x0; x <= x1; x++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                BlockPos p = base.offset(x, 3, z);
                if (z == z0 - 1) set(l, p, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                else if (z == z1 + 1) set(l, p, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                else {
                    set(l, p, planks);
                    set(l, p.above(), snow(2));
                }
            }
        // Laternen unter dem Vordach und innen
        set(l, base.offset(x0, 2, z0 - 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        set(l, base.offset(x1, 2, z0 - 1), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    static void laterne(ServerLevel l, BlockPos p) {
        set(l, p, Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, p.above(), Blocks.SPRUCE_FENCE.defaultBlockState());
        set(l, p.above(2), Blocks.LANTERN.defaultBlockState());
    }

    /** Tanne: Stamm, kegelförmige Zweige (dauerhaftes Laub), Schnee obenauf, auf Wunsch Kugeln und Stern. */
    static void tanne(ServerLevel l, BlockPos p, int h, int r, boolean schmuck) {
        BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 0; y < h - 1; y++) set(l, p.above(y), Blocks.SPRUCE_LOG.defaultBlockState());
        for (int y = 1; y <= h; y++) {
            int rr = Math.max(0, Math.round(r * (h - y) / (float) (h - 1) + (y % 2 == 0 ? 0.4f : -0.2f)));
            for (int dx = -rr; dx <= rr; dx++)
                for (int dz = -rr; dz <= rr; dz++) {
                    if (dx * dx + dz * dz > rr * rr + rr) continue;
                    BlockPos q = p.offset(dx, y, dz);
                    if (dx == 0 && dz == 0 && y < h - 1) continue;
                    BlockState s = leaves;
                    if (schmuck && (dx * dx + dz * dz) >= rr * rr - 1 && Math.floorMod(dx * 5 + dz * 3 + y * 7, 6) == 0)
                        s = (y % 2 == 0 ? Blocks.RED_WOOL : Blocks.GOLD_BLOCK).defaultBlockState();
                    set(l, q, s);
                }
        }
        if (schmuck) set(l, p.above(h + 1), Blocks.GLOWSTONE.defaultBlockState());
        // Schnee auf allen Zweigen mit freiem Himmel darüber
        for (int y = h; y >= 1; y--)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos q = p.offset(dx, y, dz);
                    if (l.getBlockState(q).is(Blocks.SPRUCE_LEAVES) && l.getBlockState(q.above()).isAir()) set(l, q.above(), snow(1));
                }
    }

    static BlockState snow(int layers) {
        return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
    }

    /**
     * Schauraum für die Abnahme der Modelle: Reihe A (Blick nach Norden, vor einer Bretterwand), Reihe B
     * (verschiedene Ausrichtungen, gegenüber). Liefert die Mitte des Platzes.
     */
    static BlockPos schauraum(ServerLevel l, BlockPos base) {
        BlockPos c = base.offset(0, 0, 44);
        for (int x = -16; x <= 16; x++)
            for (int z = -10; z <= 10; z++) {
                for (int y = 0; y <= 10; y++) set(l, c.offset(x, y, z), Blocks.AIR.defaultBlockState());
                set(l, c.offset(x, -1, z), Math.abs(z) <= 2 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.SNOW_BLOCK.defaultBlockState());
            }
        // Bretterwände hinter beiden Reihen (für den Adventskalender) mit Laternen
        for (int x = -12; x <= 12; x++)
            for (int y = 0; y <= 3; y++) {
                set(l, c.offset(x, y, 6), x % 6 == 0 ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
                set(l, c.offset(x, y, -6), x % 6 == 0 ? Blocks.SPRUCE_LOG.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
        for (int x = -12; x <= 12; x++) {
            set(l, c.offset(x, 4, 6), snow(2));
            set(l, c.offset(x, 4, -6), snow(2));
        }
        for (int x : new int[]{-12, 12}) {
            laterne(l, c.offset(x, 0, 3));
            laterne(l, c.offset(x, 0, -3));
        }
        Block[] alle = {ModContent.STALL_SIGN, ModContent.COUNTER, ModContent.BUYER, ModContent.BOARD, ModContent.LOTTERY,
                ModContent.GIFT, ModContent.ADVENT};
        // Reihe A: alle nach Norden (zur Kamera), Adventskalender an der Wand
        for (int i = 0; i < alle.length; i++) {
            int x = -9 + i * 3;
            set(l, c.offset(x, alle[i] == ModContent.ADVENT ? 1 : 0, 5), facing(alle[i], Direction.NORTH));
        }
        // Reihe B: gedreht (Adventskalender nach Süden an der Nordwand)
        Direction[] dirs = {Direction.SOUTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.SOUTH};
        for (int i = 0; i < alle.length; i++) {
            int x = 9 - i * 3;
            set(l, c.offset(x, alle[i] == ModContent.ADVENT ? 1 : 0, -5), facing(alle[i], dirs[i]));
        }
        return c;
    }
}
