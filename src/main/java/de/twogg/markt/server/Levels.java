package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;

/** Dimension aus einer ID wie "minecraft:overworld". */
public final class Levels {
    private Levels() {}

    public static ServerLevel of(String dim) {
        Identifier id = Identifier.tryParse(dim == null ? "" : dim);
        if (id == null || MarktRuntime.server() == null) return null;
        return MarktRuntime.server().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }
}
