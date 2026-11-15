package de.twogg.markt.server;

import de.twogg.markt.core.Box;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Zählen und Nehmen von Items: im Spielerinventar und in den Lagerkisten eines Stands. */
public final class Inv {
    private Inv() {}

    /** Gleiche Ware (Item und alle Daten) wie die Vorlage. */
    public static Predicate<ItemStack> same(ItemStack proto) {
        return s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, proto);
    }

    /** Gleiches Item, unbeschädigt (Aufträge, Ankauf: Daten wie Namen egal). */
    public static Predicate<ItemStack> item(Item item) {
        return s -> !s.isEmpty() && s.is(item) && !s.isDamaged();
    }

    public static int count(ServerPlayer p, Predicate<ItemStack> f) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (f.test(s)) n += s.getCount();
        return n;
    }

    /** Genau {@code amount} nehmen (vorher mit {@link #count} prüfen). Gibt die genommenen Stapel zurück. */
    public static List<ItemStack> take(ServerPlayer p, Predicate<ItemStack> f, int amount) {
        List<ItemStack> out = new ArrayList<>();
        var items = p.getInventory().getNonEquipmentItems();
        for (int i = 0; i < items.size() && amount > 0; i++) {
            ItemStack s = items.get(i);
            if (!f.test(s)) continue;
            int n = Math.min(amount, s.getCount());
            out.add(s.split(n));
            amount -= n;
        }
        p.getInventory().setChanged();
        return out;
    }

    /** Ins Inventar, was nicht passt, fällt vor die Füße. */
    public static void give(ServerPlayer p, ItemStack stack) {
        ItemStack s = stack.copy();
        while (!s.isEmpty()) {
            ItemStack part = s.split(Math.min(s.getCount(), s.getMaxStackSize()));
            if (!p.getInventory().add(part) && !part.isEmpty()) p.drop(part, false);
        }
    }

    /** Lagerkisten (Truhe, Fass, Shulkerkiste) innerhalb des Stands. */
    public static List<Container> storage(ServerLevel level, Box box) {
        List<Container> out = new ArrayList<>();
        for (int cx = box.minX >> 4; cx <= box.maxX >> 4; cx++) {
            for (int cz = box.minZ >> 4; cz <= box.maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (!box.contains(box.dim, pos.getX(), pos.getY(), pos.getZ())) continue;
                    if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity || be instanceof ShulkerBoxBlockEntity) {
                        out.add((Container) be);
                    }
                }
            }
        }
        return out;
    }

    public static int count(List<Container> containers, Predicate<ItemStack> f) {
        int n = 0;
        for (Container c : containers) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (f.test(s)) n += s.getCount();
            }
        }
        return n;
    }

    public static List<ItemStack> take(List<Container> containers, Predicate<ItemStack> f, int amount) {
        List<ItemStack> out = new ArrayList<>();
        for (Container c : containers) {
            boolean changed = false;
            for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
                ItemStack s = c.getItem(i);
                if (!f.test(s)) continue;
                int n = Math.min(amount, s.getCount());
                out.add(c.removeItem(i, n));
                amount -= n;
                changed = true;
            }
            if (changed) c.setChanged();
            if (amount <= 0) break;
        }
        return out;
    }
}
