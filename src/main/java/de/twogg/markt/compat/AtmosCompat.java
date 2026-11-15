package de.twogg.markt.compat;

import de.twogg.markt.TwoggMarkt;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Set;

/** Optionale Anbindung an TwoGG Atmos (Stimmung im Marktgelände), ohne harte Abhängigkeit – per Reflection. */
public final class AtmosCompat {
    private static Boolean loaded;
    private static Method setOverride, clearOverride, presets;

    private AtmosCompat() {}

    public static boolean loaded() {
        if (loaded == null) {
            loaded = false;
            if (FabricLoader.getInstance().isModLoaded("atmos")) {
                try {
                    Class<?> api = Class.forName("de.twogg.atmos.api.AtmosApi");
                    setOverride = api.getMethod("setOverride", ServerPlayer.class, String.class, float.class);
                    clearOverride = api.getMethod("clearOverride", ServerPlayer.class, float.class);
                    presets = api.getMethod("presets");
                    loaded = true;
                } catch (ReflectiveOperationException e) {
                    TwoggMarkt.LOGGER.warn("TwoGG Atmos gefunden, aber nicht ansprechbar ({}).", e.toString());
                }
            }
        }
        return loaded;
    }

    public static boolean set(ServerPlayer p, String preset, float fade) {
        if (!loaded()) return false;
        try {
            return (boolean) setOverride.invoke(null, p, preset, fade);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void clear(ServerPlayer p, float fade) {
        if (!loaded()) return;
        try {
            clearOverride.invoke(null, p, fade);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<String> presets() {
        if (!loaded()) return Set.of();
        try {
            return (Set<String>) presets.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Set.of();
        }
    }
}
