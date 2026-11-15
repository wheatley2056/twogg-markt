package de.twogg.markt.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** ItemStack ↔ JSON (mit allen Daten-Komponenten) über den Codec des Spiels. */
public final class Stacks {
    private Stacks() {}

    public static JsonElement toJson(HolderLookup.Provider reg, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return ItemStack.CODEC.encodeStart(reg.createSerializationContext(JsonOps.INSTANCE), stack).result().orElse(null);
    }

    /** Leerer Stapel, wenn nicht lesbar (Item entfernt, Mod fehlt). */
    public static ItemStack fromJson(HolderLookup.Provider reg, JsonElement json) {
        if (json == null || json.isJsonNull()) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(reg.createSerializationContext(JsonOps.INSTANCE), json).result().orElse(ItemStack.EMPTY);
    }

    public static String toString(HolderLookup.Provider reg, ItemStack stack) {
        JsonElement j = toJson(reg, stack);
        return j == null ? "" : j.toString();
    }

    public static ItemStack fromString(HolderLookup.Provider reg, String s) {
        if (s == null || s.isEmpty()) return ItemStack.EMPTY;
        try {
            return fromJson(reg, com.google.gson.JsonParser.parseString(s));
        } catch (RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }

    /** Item zu einer ID, oder null. */
    public static Item item(String id) {
        Identifier rl = Identifier.tryParse(id == null ? "" : id);
        if (rl == null) return null;
        Item i = BuiltInRegistries.ITEM.getValue(rl);
        return i == null || i == Items.AIR ? null : i;
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String id(ItemStack stack) {
        return id(stack.getItem());
    }
}
